import { createHash, createHmac } from "node:crypto";

/**
 * Just enough STUN and TURN to ask a server for a relay address.
 *
 * Kept apart from the checking script and free of I/O, because the fiddly parts here — where the
 * length field points when MESSAGE-INTEGRITY is computed, how an address is un-XORed — are
 * exactly the parts that fail silently. A wrong signature comes back as "401 Unauthorized", which
 * looks identical to a wrong password, so this is verified against the official test vectors in
 * RFC 5769 rather than against a server that might be refusing it for some other reason.
 */

export const MAGIC_COOKIE = 0x2112a442;
const MAGIC_BYTES = Buffer.from([0x21, 0x12, 0xa4, 0x42]);

export const METHOD = { BINDING: 0x0001, ALLOCATE: 0x0003 } as const;
export const CLASS = { REQUEST: 0x0000, SUCCESS: 0x0100, ERROR: 0x0110 } as const;

export const ATTR = {
  USERNAME: 0x0006,
  MESSAGE_INTEGRITY: 0x0008,
  ERROR_CODE: 0x0009,
  LIFETIME: 0x000d,
  REALM: 0x0014,
  NONCE: 0x0015,
  XOR_RELAYED_ADDRESS: 0x0016,
  REQUESTED_TRANSPORT: 0x0019,
  XOR_MAPPED_ADDRESS: 0x0020,
  SOFTWARE: 0x8022,
} as const;

export interface StunMessage {
  type: number;
  attributes: Map<number, Buffer>;
  total: number;
}

export interface Address {
  address: string;
  port: number;
}

/** Attributes are padded to a multiple of four; the padding is not counted in the length. */
export function attribute(type: number, value: Buffer): Buffer {
  const padding = (4 - (value.length % 4)) % 4;
  const buffer = Buffer.alloc(4 + value.length + padding);
  buffer.writeUInt16BE(type, 0);
  buffer.writeUInt16BE(value.length, 2);
  value.copy(buffer, 4);
  return buffer;
}

export function message(
  method: number,
  cls: number,
  transactionId: Buffer,
  attributes: Buffer[],
): Buffer {
  const body = Buffer.concat(attributes);
  const header = Buffer.alloc(20);
  header.writeUInt16BE(method | cls, 0);
  header.writeUInt16BE(body.length, 2);
  MAGIC_BYTES.copy(header, 4);
  transactionId.copy(header, 8);
  return Buffer.concat([header, body]);
}

/**
 * Appends MESSAGE-INTEGRITY.
 *
 * The HMAC covers everything before the attribute, but with the header's length field *already*
 * counting the twenty-four bytes it is about to occupy. Computing it over the unmodified header
 * is the classic way to produce a signature every server rejects.
 */
export function withIntegrity(request: Buffer, key: Buffer): Buffer {
  const framed = Buffer.from(request);
  framed.writeUInt16BE(framed.length - 20 + 24, 2);
  const digest = createHmac("sha1", key).update(framed).digest();
  // `framed`, not `request`: the length that was signed is also the length that has to go out,
  // or the server parses the message without the attribute it was asked to verify.
  return Buffer.concat([framed, attribute(ATTR.MESSAGE_INTEGRITY, digest)]);
}

/** RFC 5389's long-term credential key. SASLprep is a no-op for anything this app produces. */
export function longTermKey(username: string, realm: string, password: string): Buffer {
  return createHash("md5").update(`${username}:${realm}:${password}`).digest();
}

export function parse(buffer: Buffer): StunMessage | null {
  if (buffer.length < 20) return null;
  const type = buffer.readUInt16BE(0);
  const length = buffer.readUInt16BE(2);
  if (buffer.length < 20 + length) return null;

  const attributes = new Map<number, Buffer>();
  let offset = 20;
  while (offset + 4 <= 20 + length) {
    const attributeType = buffer.readUInt16BE(offset);
    const attributeLength = buffer.readUInt16BE(offset + 2);
    const value = buffer.subarray(offset + 4, offset + 4 + attributeLength);
    // First wins: a server repeating an attribute does not get to overwrite the one in force.
    if (!attributes.has(attributeType)) attributes.set(attributeType, value);
    offset += 4 + attributeLength + ((4 - (attributeLength % 4)) % 4);
  }
  return { type, attributes, total: 20 + length };
}

/**
 * The method, dug back out of a message type.
 *
 * STUN does not keep the method in one piece: the two class bits sit *inside* it, at bit 4 and
 * bit 8, so the method's bits are split into three runs around them. A binding request is 0x0001
 * and its success response is 0x0101 — the same method, and not a mask apart.
 */
export function methodOf(type: number): number {
  return (type & 0x000f) | ((type & 0x00e0) >> 1) | ((type & 0x3e00) >> 2);
}

export function classOf(type: number): number {
  return type & 0x0110;
}

export function isSuccess(type: number, method: number): boolean {
  return classOf(type) === CLASS.SUCCESS && methodOf(type) === methodOf(method);
}

/** Addresses are XORed with the magic cookie so a NAT rewriting payloads cannot spot them. */
export function xorAddress(value: Buffer | undefined): Address | null {
  if (value === undefined || value.length < 8) return null;
  const family = value.readUInt8(1);
  const port = value.readUInt16BE(2) ^ (MAGIC_COOKIE >>> 16);
  if (family !== 0x01) return { address: "(ipv6)", port };
  const octets: number[] = [];
  for (let i = 0; i < 4; i++) octets.push(value.readUInt8(4 + i) ^ MAGIC_BYTES.readUInt8(i));
  return { address: octets.join("."), port };
}

export function errorOf(attributes: Map<number, Buffer>): { code: number; reason: string } | null {
  const value = attributes.get(ATTR.ERROR_CODE);
  if (value === undefined || value.length < 4) return null;
  return {
    code: value.readUInt8(2) * 100 + value.readUInt8(3),
    reason: value.subarray(4).toString("utf8"),
  };
}

export interface IceTarget {
  raw: string;
  host: string;
  port: number;
  transport: "udp" | "tcp" | "tls";
  isTurn: boolean;
}

/**
 * `turn:host:3478?transport=udp` is not a URL any parser will take, so it comes apart by hand.
 *
 * `turns:` is always TLS over TCP whatever the query string claims, and the default port depends
 * on the scheme: 5349 for the secure ones, 3478 otherwise.
 */
export function parseIceUrl(raw: string): IceTarget | null {
  const [address = "", query = ""] = raw.split("?");
  const match = address.match(/^(stuns?|turns?):(\[[^\]]+\]|[^:]+)(?::(\d+))?$/);
  if (match === null) return null;
  const [, scheme = "", host = "", port] = match;
  const secure = scheme.endsWith("s");
  return {
    raw,
    host: host.replace(/^\[|\]$/g, ""),
    port: Number.parseInt(port ?? (secure ? "5349" : "3478"), 10),
    transport: secure ? "tls" : /transport=tcp/i.test(query) ? "tcp" : "udp",
    isTurn: scheme.startsWith("turn"),
  };
}
