import { randomBytes } from "node:crypto";
import { createSocket } from "node:dgram";
import { connect as tcpConnect, type Socket } from "node:net";
import { connect as tlsConnect } from "node:tls";
import type { IceServer } from "./protocol.js";
import {
  ATTR,
  CLASS,
  METHOD,
  attribute,
  errorOf,
  isSuccess,
  longTermKey,
  message,
  parse,
  parseIceUrl,
  withIntegrity,
  xorAddress,
  type Address,
  type IceTarget,
  type StunMessage,
} from "./stun.js";

/**
 * Does TURN actually work?
 *
 * This is the question that decides whether a call connects between two phones that cannot reach
 * each other directly, and nothing else here can answer it. A relay handing out TURN URLs proves
 * only that someone typed them into an environment variable: the credentials may be dead, the
 * port may be blocked, the provider may have moved. The only proof is asking the server for a
 * relay address and being given one.
 *
 *     npm run check:turn                      # reads TURN_URLS and friends from the environment
 *     npm run check:turn -- https://relay...  # asks a running relay what it hands out
 *
 * Run it from the network you are worried about. Passing on a home connection says the
 * credentials are good; it says nothing about a network that blocks calls, and that is the case
 * this app was built for.
 */

const TIMEOUT_MS = 6_000;

interface Exchange {
  reply: StunMessage | null;
  connection: Socket | null;
}

function overUdp(target: IceTarget, request: Buffer): Promise<Exchange> {
  return new Promise((resolve, reject) => {
    const socket = createSocket("udp4");
    const timer = setTimeout(() => {
      socket.close();
      reject(new Error(`no answer within ${TIMEOUT_MS}ms`));
    }, TIMEOUT_MS);

    socket.on("message", (data) => {
      clearTimeout(timer);
      socket.close();
      resolve({ reply: parse(data), connection: null });
    });
    socket.on("error", (error) => {
      clearTimeout(timer);
      socket.close();
      reject(error);
    });
    socket.send(request, target.port, target.host);
  });
}

/**
 * TCP and TLS keep the connection open between the two halves of the exchange: a TURN allocation
 * belongs to its connection, so the signed retry has to go back down the socket that was
 * challenged rather than a fresh one.
 */
function overStream(target: IceTarget, request: Buffer, previous: Socket | null): Promise<Exchange> {
  return new Promise((resolve, reject) => {
    const socket: Socket =
      previous ??
      (target.transport === "tls"
        ? (tlsConnect({ host: target.host, port: target.port, servername: target.host }) as Socket)
        : tcpConnect({ host: target.host, port: target.port }));

    let buffered = Buffer.alloc(0);

    const cleanup = (): void => {
      clearTimeout(timer);
      socket.off("data", onData);
      socket.off("error", onError);
    };
    const onData = (chunk: Buffer): void => {
      buffered = Buffer.concat([buffered, chunk]);
      const reply = parse(buffered);
      if (reply === null) return;
      cleanup();
      resolve({ reply, connection: socket });
    };
    const onError = (error: Error): void => {
      cleanup();
      socket.destroy();
      reject(error);
    };
    const timer = setTimeout(() => {
      cleanup();
      socket.destroy();
      reject(new Error(`no answer within ${TIMEOUT_MS}ms`));
    }, TIMEOUT_MS);

    socket.on("data", onData);
    socket.on("error", onError);

    if (previous !== null) socket.write(request);
    else {
      socket.once(target.transport === "tls" ? "secureConnect" : "connect", () =>
        socket.write(request),
      );
    }
  });
}

function exchange(target: IceTarget, request: Buffer, previous: Socket | null): Promise<Exchange> {
  return target.transport === "udp" ? overUdp(target, request) : overStream(target, request, previous);
}

async function checkStun(target: IceTarget): Promise<Address> {
  const request = message(METHOD.BINDING, CLASS.REQUEST, randomBytes(12), [
    attribute(ATTR.SOFTWARE, Buffer.from("auralis-check")),
  ]);
  const { reply, connection } = await exchange(target, request, null);
  connection?.destroy();
  if (reply === null || !isSuccess(reply.type, METHOD.BINDING)) {
    throw new Error("not a binding success");
  }
  const seen = xorAddress(reply.attributes.get(ATTR.XOR_MAPPED_ADDRESS));
  if (seen === null) throw new Error("no address came back");
  return seen;
}

async function checkTurn(target: IceTarget, username: string, password: string): Promise<Address> {
  const body = (): Buffer[] => [
    // UDP between the TURN server and the far side, which is what a call uses.
    attribute(ATTR.REQUESTED_TRANSPORT, Buffer.from([17, 0, 0, 0])),
    attribute(ATTR.LIFETIME, Buffer.from([0, 0, 0, 60])),
  ];

  // The first Allocate is expected to be refused. The refusal is what carries the realm and the
  // nonce the signed one has to quote back.
  const challenge = await exchange(
    target,
    message(METHOD.ALLOCATE, CLASS.REQUEST, randomBytes(12), body()),
    null,
  );
  const challenged = challenge.reply;
  if (challenged === null) throw new Error("no answer to allocate");

  if (isSuccess(challenged.type, METHOD.ALLOCATE)) {
    // An open relay that wants no credentials at all.
    challenge.connection?.destroy();
    const relayed = xorAddress(challenged.attributes.get(ATTR.XOR_RELAYED_ADDRESS));
    if (relayed === null) throw new Error("allocated, but no relayed address came back");
    return relayed;
  }

  const failure = errorOf(challenged.attributes);
  if (failure === null || (failure.code !== 401 && failure.code !== 438)) {
    challenge.connection?.destroy();
    throw new Error(failure ? `${failure.code} ${failure.reason}` : "refused without a reason");
  }

  const realm = challenged.attributes.get(ATTR.REALM);
  const nonce = challenged.attributes.get(ATTR.NONCE);
  if (realm === undefined || nonce === undefined) {
    challenge.connection?.destroy();
    throw new Error("challenged without a realm or nonce");
  }

  const signed = withIntegrity(
    message(METHOD.ALLOCATE, CLASS.REQUEST, randomBytes(12), [
      ...body(),
      attribute(ATTR.USERNAME, Buffer.from(username, "utf8")),
      attribute(ATTR.REALM, realm),
      attribute(ATTR.NONCE, nonce),
    ]),
    longTermKey(username, realm.toString("utf8"), password),
  );

  const answer = await exchange(target, signed, challenge.connection);
  answer.connection?.destroy();
  const allocated = answer.reply;
  if (allocated === null) throw new Error("no answer to the signed allocate");
  if (!isSuccess(allocated.type, METHOD.ALLOCATE)) {
    const refusal = errorOf(allocated.attributes);
    throw new Error(refusal ? `${refusal.code} ${refusal.reason}` : "refused without a reason");
  }

  const relayed = xorAddress(allocated.attributes.get(ATTR.XOR_RELAYED_ADDRESS));
  if (relayed === null) throw new Error("allocated, but no relayed address came back");
  return relayed;
}

// --- where the list comes from ------------------------------------------------------------------

async function fromRelay(url: string): Promise<IceServer[]> {
  const base = url.replace(/\/+$/, "");
  const room = (await fetch(`${base}/rooms`, { method: "POST" }).then((r) => r.json())) as {
    code: string;
    hostToken: string;
  };
  const { default: WebSocket } = await import("ws");
  const socket = new WebSocket(
    `${base.replace(/^http/, "ws")}/rooms/${room.code}/ws?token=${room.hostToken}&name=check`,
  );

  return await new Promise<IceServer[]>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("the relay did not answer")), 15_000);
    socket.on("open", () => socket.send(JSON.stringify({ type: "ice" })));
    socket.on("message", (raw: Buffer) => {
      const parsed = JSON.parse(raw.toString()) as { type: string; iceServers?: IceServer[] };
      if (parsed.type !== "ice") return;
      clearTimeout(timer);
      socket.close();
      resolve(parsed.iceServers ?? []);
    });
    socket.on("error", reject);
  });
}

function fromEnv(): IceServer[] {
  const split = (value: string | undefined): string[] =>
    (value ?? "")
      .split(",")
      .map((entry) => entry.trim())
      .filter(Boolean);

  const stun = split(process.env.STUN_URLS);
  const servers: IceServer[] = [{ urls: stun.length > 0 ? stun : ["stun:stun.l.google.com:19302"] }];

  const turn = split(process.env.TURN_URLS);
  if (turn.length > 0) {
    servers.push({
      urls: turn,
      username: process.env.TURN_USERNAME,
      credential: process.env.TURN_CREDENTIAL,
    });
  }
  return servers;
}

// --- run ------------------------------------------------------------------------------------------

const relayUrl = process.argv[2];
const servers = relayUrl ? await fromRelay(relayUrl) : fromEnv();

let turnUrls = 0;
let turnWorking = 0;
let tlsOn443Working = false;

for (const server of servers) {
  for (const raw of server.urls ?? []) {
    const target = parseIceUrl(raw);
    if (target === null) {
      console.log(`SKIP ${raw} — could not read that address`);
      continue;
    }

    if (!target.isTurn) {
      try {
        const seen = await checkStun(target);
        console.log(`OK   ${raw} — it sees you as ${seen.address}:${seen.port}`);
      } catch (error) {
        console.log(`FAIL ${raw} — ${(error as Error).message}`);
      }
      continue;
    }

    turnUrls++;
    if (!server.username || !server.credential) {
      console.log(`FAIL ${raw} — a TURN address with no credentials cannot be used`);
      continue;
    }

    try {
      const relayed = await checkTurn(target, server.username, server.credential);
      turnWorking++;
      if (target.transport === "tls" && target.port === 443) tlsOn443Working = true;
      console.log(`OK   ${raw} — relays through ${relayed.address}:${relayed.port}`);
    } catch (error) {
      console.log(`FAIL ${raw} — ${(error as Error).message}`);
    }
  }
}

console.log("");
if (turnUrls === 0) {
  console.log("No TURN configured. A call will connect when both networks allow a direct path and");
  console.log("fail when they do not. See docs/RELAY.md.");
  process.exit(1);
}
if (turnWorking === 0) {
  console.log("Every TURN address failed, which is worse than having none: each dead address is a");
  console.log("connection attempt the call waits on before giving up.");
  process.exit(1);
}
if (!tlsOn443Working) {
  console.log("TURN works, but not over TLS on 443 — the one that gets through a network hostile");
  console.log("to calls, which is the case this app was built for.");
  process.exit(1);
}
console.log("TURN works, including TLS on 443. A call has a way through a restrictive network.");
