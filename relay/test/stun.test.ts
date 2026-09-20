import { describe, expect, it } from "vitest";
import {
  ATTR,
  CLASS,
  METHOD,
  classOf,
  methodOf,
  attribute,
  isSuccess,
  longTermKey,
  message,
  parse,
  parseIceUrl,
  withIntegrity,
  xorAddress,
} from "../src/stun.js";

/**
 * Checked against RFC 5769, which exists precisely because these are the bytes everyone gets
 * wrong. A bad MESSAGE-INTEGRITY comes back as "401 Unauthorized", indistinguishable from a bad
 * password — so a TURN check built on an unverified codec would confidently report the wrong
 * cause, which is worse than not checking at all.
 */
const hex = (value: string) => Buffer.from(value, "hex");

describe("RFC 5769 test vectors", () => {
  // 2.2. Sample IPv4 Response — a binding success from 192.0.2.1:32853.
  const ipv4Response = hex(
    "0101003c2112a442b7e7a701bc34d686fa87dfae8022000b7465737420766563746f722000" +
      "2000080001a147e112a643000800142b91f599fd9e90c38c7489f92af9ba53f06be7d780280004c07d4c96",
  );

  it("reads a binding response and un-XORs the address in it", () => {
    const parsed = parse(ipv4Response);
    expect(parsed).not.toBeNull();
    expect(isSuccess(parsed!.type, METHOD.BINDING)).toBe(true);
    expect(xorAddress(parsed!.attributes.get(ATTR.XOR_MAPPED_ADDRESS))).toEqual({
      address: "192.0.2.1",
      port: 32853,
    });
  });

  it("finds the method again, split around the class bits as STUN leaves it", () => {
    // A binding request is 0x0001 and its success response 0x0101 — the same method, and not a
    // mask apart. Treating them as one would make every good response look like a failure.
    expect(methodOf(0x0001)).toBe(methodOf(0x0101));
    expect(methodOf(0x0101)).toBe(METHOD.BINDING);
    expect(methodOf(0x0113)).toBe(METHOD.ALLOCATE);
    expect(classOf(0x0103)).toBe(CLASS.SUCCESS);
    expect(classOf(0x0113)).toBe(CLASS.ERROR);
    expect(isSuccess(0x0113, METHOD.ALLOCATE)).toBe(false);
    expect(isSuccess(0x0103, METHOD.ALLOCATE)).toBe(true);
    expect(isSuccess(0x0101, METHOD.ALLOCATE)).toBe(false);
  });

  it("ignores trailing bytes beyond the length the header declares", () => {
    const withJunk = Buffer.concat([ipv4Response, hex("deadbeef")]);
    expect(parse(withJunk)?.total).toBe(ipv4Response.length);
  });

  it("returns null for a message that has not fully arrived", () => {
    expect(parse(ipv4Response.subarray(0, 40))).toBeNull();
    expect(parse(hex("0101"))).toBeNull();
  });

  // 2.4. Sample Request with Long-Term Authentication.
  const longTermRequest = hex(
    "000100602112a44278ad3433c6ad72c029da412e00060012e3839ee38388e383aae38383" +
      "e382afe382b900000015001c662f2f3439396b39353464364f4c33346f4c3946535476793634734100" +
      "14000b6578616d706c652e6f72670000080014f67024656dd64a3e02b8e0712e85c9a28ca89666",
  );

  const username = "マトリックス";
  const realm = "example.org";
  const password = "TheMatrIX";

  it("signs a long-term authenticated request exactly as the RFC does", () => {
    const request = message(METHOD.BINDING, CLASS.REQUEST, hex("78ad3433c6ad72c029da412e"), [
      attribute(ATTR.USERNAME, Buffer.from(username, "utf8")),
      attribute(ATTR.NONCE, Buffer.from("f//499k954d6OL34oL9FSTvy64sA", "utf8")),
      attribute(ATTR.REALM, Buffer.from(realm, "utf8")),
    ]);

    const signed = withIntegrity(request, longTermKey(username, realm, password));

    expect(signed.toString("hex")).toBe(longTermRequest.toString("hex"));
  });

  it("puts the length field where the signature expects it, not where the bytes end", () => {
    // The published signature only matches if the header counted MESSAGE-INTEGRITY before it
    // existed. This is the mistake that reads back as a wrong password.
    const signed = withIntegrity(
      message(METHOD.BINDING, CLASS.REQUEST, hex("78ad3433c6ad72c029da412e"), [
        attribute(ATTR.USERNAME, Buffer.from(username, "utf8")),
        attribute(ATTR.NONCE, Buffer.from("f//499k954d6OL34oL9FSTvy64sA", "utf8")),
        attribute(ATTR.REALM, Buffer.from(realm, "utf8")),
      ]),
      longTermKey(username, realm, password),
    );
    expect(signed.readUInt16BE(2)).toBe(signed.length - 20);
    expect(signed.subarray(-20).toString("hex")).toBe("f67024656dd64a3e02b8e0712e85c9a28ca89666");
  });

  it("reads the attributes back out of what it wrote", () => {
    const parsed = parse(longTermRequest);
    expect(parsed?.attributes.get(ATTR.REALM)?.toString("utf8")).toBe(realm);
    expect(parsed?.attributes.get(ATTR.USERNAME)?.toString("utf8")).toBe(username);
  });
});

describe("ice urls", () => {
  it("defaults the port from the scheme", () => {
    expect(parseIceUrl("stun:stun.example.com")).toMatchObject({ port: 3478, transport: "udp" });
    expect(parseIceUrl("turns:turn.example.com")).toMatchObject({ port: 5349, transport: "tls" });
  });

  it("treats turns as TLS whatever the query string says", () => {
    // The transport parameter describes the connection to the TURN server, and for turns: it is
    // TLS by definition. Believing "transport=udp" here would open a plaintext socket.
    expect(parseIceUrl("turns:turn.example.com:443?transport=udp")).toMatchObject({
      port: 443,
      transport: "tls",
      isTurn: true,
    });
  });

  it("separates the ones that can relay from the ones that only report an address", () => {
    expect(parseIceUrl("turn:turn.example.com:3478?transport=tcp")).toMatchObject({
      transport: "tcp",
      isTurn: true,
    });
    expect(parseIceUrl("stun:stun.example.com:19302")?.isTurn).toBe(false);
  });

  it("refuses anything that is not a stun or turn address", () => {
    expect(parseIceUrl("https://example.com")).toBeNull();
    expect(parseIceUrl("javascript:alert(1)")).toBeNull();
  });
});
