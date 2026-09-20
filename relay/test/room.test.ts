import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { beforeEach, describe, expect, it } from "vitest";
import { iceConfigFromEnv, mintIceServers, restCredential } from "../src/ice.js";
import { Room, type Connection, type Sink } from "../src/room.js";
import { RoomStore } from "../src/store.js";
import type { ServerMessage } from "../src/protocol.js";

/**
 * The room, tested directly.
 *
 * This is the half that used to need a Workers runtime to exercise at all, and therefore was only
 * covered by a smoke test against a deployment. As a plain object over a [Sink] it runs here, so
 * presence, host handover and what a hostile frame does to a *running* room are checked on every
 * push rather than after every deploy.
 */

class FakeSocket implements Sink {
  readonly received: ServerMessage[] = [];
  closedWith: number | null = null;

  send(data: string): void {
    this.received.push(JSON.parse(data) as ServerMessage);
  }

  close(code?: number): void {
    this.closedWith = code ?? 1000;
  }

  of<T extends ServerMessage["type"]>(type: T): Extract<ServerMessage, { type: T }>[] {
    return this.received.filter((m): m is Extract<ServerMessage, { type: T }> => m.type === type);
  }

  last<T extends ServerMessage["type"]>(type: T): Extract<ServerMessage, { type: T }> | undefined {
    return this.of(type).at(-1);
  }
}

const track = {
  provider: "audius",
  providerId: "abc",
  title: "A Song",
  artist: "An Artist",
  durationMs: 210_000,
};

describe("a room", () => {
  let store: RoomStore;
  let room: Room;
  let clock = 1_000;

  const ice = iceConfigFromEnv({});

  beforeEach(() => {
    clock = 1_000;
    store = new RoomStore(null, 30 * 24 * 60 * 60 * 1000);
    const record = store.create("AB2CD3", "host-token-0123456789", clock);
    if (record === null) throw new Error("fixture room was taken");
    room = new Room(record, store, ice, () => clock);
  });

  function join(name: string, token = `token-${name}-0123456789`) {
    const socket = new FakeSocket();
    const connection = room.join(socket, { name, token, timeZone: "Asia/Kolkata", memberId: name });
    if (connection === "room_full") throw new Error("room full");
    return { socket, connection };
  }

  function send(connection: Connection, message: unknown) {
    room.receive(connection, JSON.stringify(message));
  }

  it("welcomes the first member and makes them host", () => {
    const { socket } = join("her");
    const welcome = socket.last("welcome");
    expect(welcome?.memberId).toBe("her");
    expect(welcome?.hostId).toBe("her");
    expect(welcome?.serverMs).toBe(clock);
  });

  it("stamps who sent a message and when, rather than believing the sender", () => {
    const her = join("her");
    const him = join("him");
    clock = 5_000;

    // The client says nothing about identity or time; both are the server's to assign.
    send(him.connection, { type: "playback", track, positionMs: 42, isPlaying: true, speed: 1 });

    const relayed = her.socket.last("playback");
    expect(relayed?.senderId).toBe("him");
    expect(relayed?.serverMs).toBe(5_000);
    expect(relayed?.positionMs).toBe(42);
  });

  it("refuses a frame carrying a stream url, and never passes it on", () => {
    const her = join("her");
    const him = join("him");

    send(him.connection, {
      type: "playback",
      track: { ...track, mediaUrl: "https://evil.invalid/payload.mp3" },
      positionMs: 0,
      isPlaying: true,
      speed: 1,
    });

    expect(him.socket.last("error")?.message).toMatch(/mediaUrl/);
    expect(JSON.stringify(her.socket.received)).not.toContain("evil.invalid");
  });

  it("hands the room over when the host leaves rather than stranding it", () => {
    const her = join("her");
    const him = join("him");

    room.leave(her.connection);

    const presence = him.socket.last("presence");
    expect(presence?.hostId).toBe("him");
    expect(presence?.members.map((m) => m.id)).toEqual(["him"]);
  });

  it("gives the room back to whoever holds the host token", () => {
    join("her");
    // The original host reconnecting: same token, new socket and member id.
    const { socket } = join("her-again", "host-token-0123456789");
    expect(socket.last("welcome")?.hostId).toBe("her-again");
  });

  it("keeps the last messages so a rejoin sees what it missed", () => {
    const her = join("her");
    send(her.connection, { type: "chat", ciphertext: "AAECAwQFBgc=" });

    const { socket } = join("him");
    expect(socket.last("welcome")?.state.chat).toEqual([
      { senderId: "her", serverMs: clock, ciphertext: "AAECAwQFBgc=" },
    ]);
  });

  it("drops the oldest message rather than growing without limit", () => {
    const her = join("her");
    for (let i = 0; i < 60; i++) {
      // Spaced out: sixty messages in the same millisecond is a flood, and the rate limiter
      // would eat most of them before the history cap ever came into it.
      clock += 200;
      send(her.connection, { type: "chat", ciphertext: `msg-${i}` });
    }
    const { socket } = join("him");
    const chat = socket.last("welcome")?.state.chat ?? [];
    expect(chat).toHaveLength(50);
    expect(chat[0]?.ciphertext).toBe("msg-10");
  });

  it("removes one copy of a duplicated queue entry, not both", () => {
    const her = join("her");
    send(her.connection, { type: "queueAdd", track });
    send(her.connection, { type: "queueAdd", track: { ...track, providerId: "other" } });
    send(her.connection, { type: "queueAdd", track });

    send(her.connection, { type: "queueRemove", providerId: "abc" });

    expect(her.socket.last("queue")?.items.map((i) => i.track.providerId)).toEqual(["other", "abc"]);
  });

  it("slows down a member who floods it", () => {
    const her = join("her");
    // Twenty is the burst; the twenty-first in the same millisecond is refused.
    for (let i = 0; i < 21; i++) send(her.connection, { type: "ping", at: i });
    expect(her.socket.last("error")?.code).toBe("rate_limited");
  });

  it("answers a ping with the room's clock, which is the whole point of it", () => {
    const her = join("her");
    clock = 9_999;
    send(her.connection, { type: "ping", at: 123 });
    const pong = her.socket.last("pong");
    expect(pong?.at).toBe(123);
    expect(pong?.serverMs).toBe(9_999);
  });

  it("tells a member where a call should look for the other phone", () => {
    const her = join("her");
    send(her.connection, { type: "ice" });
    const urls = her.socket.last("ice")?.iceServers.flatMap((s) => s.urls) ?? [];
    expect(urls.length).toBeGreaterThan(0);
    expect(urls.every((url) => /^stuns?:|^turns?:/.test(url))).toBe(true);
  });
});

describe("rooms on disk", () => {
  let directory: string;

  beforeEach(() => {
    directory = mkdtempSync(join(tmpdir(), "auralis-relay-"));
  });

  it("gives a couple their code back after a restart", () => {
    const first = new RoomStore(directory, 1_000_000, 0);
    const record = first.create("AB2CD3", "host-token-0123456789", 1_000);
    record!.chat.push({ senderId: "her", serverMs: 1_000, ciphertext: "AAEC" });
    first.close();

    const second = new RoomStore(directory, 1_000_000, 0);
    expect(second.get("AB2CD3")?.hostToken).toBe("host-token-0123456789");
    expect(second.get("AB2CD3")?.chat).toHaveLength(1);
    // Nobody is connected to anything yet, whatever the file said.
    expect(second.get("AB2CD3")?.hostId).toBe("");

    rmSync(directory, { recursive: true, force: true });
  });

  it("refuses a code that is already taken rather than handing over a live room", () => {
    const store = new RoomStore(null, 1_000_000, 0);
    expect(store.create("AB2CD3", "first-token-0123456789", 0)).not.toBeNull();
    expect(store.create("AB2CD3", "second-token-012345678", 0)).toBeNull();
    expect(store.get("AB2CD3")?.hostToken).toBe("first-token-0123456789");
  });

  it("drops a room nobody has touched for the whole ttl, and keeps one that was", () => {
    const ttl = 1_000;
    const store = new RoomStore(null, ttl, 0);
    store.create("OLD123", "token-0123456789abcd", 0);
    store.create("NEW123", "token-0123456789abcd", 900);

    expect(store.sweep(1_500)).toBe(1);
    expect(store.get("OLD123")).toBeUndefined();
    expect(store.get("NEW123")).toBeDefined();
  });
});

describe("ice configuration", () => {
  it("is stun only when no turn server is configured", () => {
    const servers = mintIceServers(iceConfigFromEnv({}), 0).iceServers;
    expect(servers.flatMap((s) => s.urls).every((url) => url.startsWith("stun:"))).toBe(true);
  });

  it("derives a credential that expires, so nothing long-lived reaches a phone", () => {
    const config = iceConfigFromEnv({
      TURN_URLS: "turns:turn.example.com:443?transport=tcp",
      TURN_SECRET: "shared",
      TURN_TTL_SECONDS: "3600",
    });
    const minted = mintIceServers(config, 1_000_000);
    const turn = minted.iceServers.find((s) => s.urls.some((u) => u.startsWith("turns:")));

    expect(turn?.username).toBe(`${1_000 + 3_600}:auralis`);
    expect(turn?.credential).toBe(restCredential("shared", 1_000 + 3_600).credential);
    expect(minted.expiresAtMs).toBe((1_000 + 3_600) * 1_000);
  });

  it("passes a hosted provider's static credentials straight through", () => {
    const config = iceConfigFromEnv({
      TURN_URLS: "turn:turn.example.com:80?transport=tcp",
      TURN_USERNAME: "someone",
      TURN_CREDENTIAL: "secret",
    });
    const turn = mintIceServers(config, 0).iceServers.at(-1);
    expect(turn?.username).toBe("someone");
    expect(turn?.credential).toBe("secret");
  });

  /** URLs with no way to authenticate are worse than none: each is a refused attempt a call waits on. */
  it("ignores turn urls it has no credentials for", () => {
    const config = iceConfigFromEnv({ TURN_URLS: "turn:turn.example.com:3478" });
    const urls = mintIceServers(config, 0).iceServers.flatMap((s) => s.urls);
    expect(urls.some((url) => url.startsWith("turn:"))).toBe(false);
  });
});
