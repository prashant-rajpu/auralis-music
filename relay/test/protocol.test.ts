import { describe, expect, it } from "vitest";
import {
  CODE_ALPHABET,
  CODE_LENGTH,
  MAX_MESSAGE_BYTES,
  RateLimiter,
  ValidationError,
  generateRoomCode,
  PUBLIC_STUN,
  isValidRoomCode,
  normaliseIceServers,
  parseClientMessage,
  removeFirstMatch,
  validateTrackRef,
} from "../src/protocol";

const validTrack = {
  provider: "audius",
  providerId: "abc123",
  title: "A Song",
  artist: "An Artist",
  durationMs: 210_000,
};

function parse(value: unknown) {
  return parseClientMessage(JSON.stringify(value));
}

describe("the relay never carries a stream URL", () => {
  it("rejects a track carrying a mediaUrl", () => {
    expect(() =>
      validateTrackRef({ ...validTrack, mediaUrl: "https://evil.invalid/payload.mp3" }),
    ).toThrow(/unexpected field "mediaUrl"/);
  });

  it("rejects a track carrying a url under any other name", () => {
    for (const field of ["url", "streamUrl", "src", "audioUrl", "href"]) {
      expect(() => validateTrackRef({ ...validTrack, [field]: "https://evil.invalid/a.mp3" })).toThrow(
        ValidationError,
      );
    }
  });

  it("rejects a playback message that smuggles a url past the track", () => {
    expect(() =>
      parse({
        type: "playback",
        track: validTrack,
        positionMs: 0,
        isPlaying: true,
        speed: 1,
        mediaUrl: "https://evil.invalid/a.mp3",
      }),
    ).toThrow(/unexpected field "mediaUrl"/);
  });

  it("accepts cover art only over https", () => {
    expect(validateTrackRef({ ...validTrack, artUrl: "https://cdn.invalid/a.jpg" }).artUrl).toBe(
      "https://cdn.invalid/a.jpg",
    );
    for (const bad of [
      "http://cdn.invalid/a.jpg",
      "file:///etc/passwd",
      "javascript:alert(1)",
      "data:image/png;base64,AAA",
      "not a url",
    ]) {
      expect(() => validateTrackRef({ ...validTrack, artUrl: bad })).toThrow(ValidationError);
    }
  });
});

describe("track validation", () => {
  it("accepts a well-formed reference", () => {
    expect(validateTrackRef(validTrack)).toEqual(validTrack);
  });

  it("rejects an unknown provider", () => {
    expect(() => validateTrackRef({ ...validTrack, provider: "spotify" })).toThrow(
      /not a known provider/,
    );
  });

  it("rejects an empty provider id", () => {
    expect(() => validateTrackRef({ ...validTrack, providerId: "" })).toThrow(/must not be empty/);
  });

  it("rejects a negative duration", () => {
    expect(() => validateTrackRef({ ...validTrack, durationMs: -1 })).toThrow(/negative/);
  });

  it("rejects non-finite numbers", () => {
    for (const value of [Number.NaN, Number.POSITIVE_INFINITY]) {
      expect(() => validateTrackRef({ ...validTrack, durationMs: value })).toThrow(/finite/);
    }
  });

  it("caps absurdly long strings rather than relaying them", () => {
    expect(() => validateTrackRef({ ...validTrack, title: "x".repeat(600) })).toThrow(/longer than/);
  });
});

describe("message parsing", () => {
  it("refuses a frame over the size cap before parsing it", () => {
    const huge = "x".repeat(MAX_MESSAGE_BYTES + 1);
    try {
      parseClientMessage(huge);
      throw new Error("expected a rejection");
    } catch (error) {
      expect(error).toBeInstanceOf(ValidationError);
      expect((error as ValidationError).code).toBe("too_large");
    }
  });

  it("refuses malformed JSON", () => {
    expect(() => parseClientMessage("{not json")).toThrow(/valid JSON/);
  });

  it("refuses an unknown message type", () => {
    expect(() => parse({ type: "exec", cmd: "rm -rf /" })).toThrow(/unknown message type/);
  });

  it("accepts each supported message", () => {
    expect(parse({ type: "ping", at: 1 })).toEqual({ type: "ping", at: 1 });
    expect(parse({ type: "seek", positionMs: 500 })).toEqual({ type: "seek", positionMs: 500 });
    expect(parse({ type: "buffering", buffering: true })).toEqual({
      type: "buffering",
      buffering: true,
    });
    expect(parse({ type: "reaction", emoji: "🔥" })).toEqual({ type: "reaction", emoji: "🔥" });
    expect(parse({ type: "bye" })).toEqual({ type: "bye" });
  });

  it("carries a payload big enough for a WebRTC offer", () => {
    // Signalling rides the chat channel, so the cap has to fit an encrypted SDP.
    const sdpSized = "A".repeat(8000);
    expect(parse({ type: "chat", ciphertext: sdpSized })).toEqual({
      type: "chat",
      ciphertext: sdpSized,
    });
  });

  it("still refuses a payload past the cap", () => {
    expect(() => parse({ type: "chat", ciphertext: "A".repeat(20_000) })).toThrow(ValidationError);
  });

  it("keeps chat opaque — the relay only ever sees ciphertext", () => {
    const message = parse({ type: "chat", ciphertext: "AAECAwQ=" });
    expect(message).toEqual({ type: "chat", ciphertext: "AAECAwQ=" });
    // There is deliberately no plaintext field to send.
    expect(() => parse({ type: "chat", text: "hello" })).toThrow(ValidationError);
  });

  it("rejects a playback speed that would make drift maths explode", () => {
    for (const speed of [0, -1, 100, 0.1]) {
      expect(() =>
        parse({ type: "playback", track: validTrack, positionMs: 0, isPlaying: true, speed }),
      ).toThrow(/speed/);
    }
    expect(parse({ type: "playback", track: validTrack, positionMs: 0, isPlaying: true, speed: 2 }))
      .toMatchObject({ speed: 2 });
  });

  it("rejects a negative position", () => {
    expect(() => parse({ type: "seek", positionMs: -1 })).toThrow(/negative/);
  });
});

describe("room codes", () => {
  it("uses an alphabet without visually ambiguous characters", () => {
    for (const confusable of ["0", "O", "1", "I", "L"]) {
      expect(CODE_ALPHABET).not.toContain(confusable);
    }
  });

  it("generates codes of the expected shape", () => {
    for (let i = 0; i < 200; i++) {
      const code = generateRoomCode();
      expect(code).toHaveLength(CODE_LENGTH);
      expect(isValidRoomCode(code)).toBe(true);
    }
  });

  it("rejects codes of the wrong length or alphabet", () => {
    expect(isValidRoomCode("ABC")).toBe(false);
    expect(isValidRoomCode("ABCDEFG")).toBe(false);
    expect(isValidRoomCode("ABCDE0")).toBe(false);
    expect(isValidRoomCode("abcdef")).toBe(false);
  });
});

describe("rate limiting", () => {
  it("allows a burst up to capacity then refuses", () => {
    const limiter = new RateLimiter(3, 1, 0);
    expect(limiter.tryConsume(0)).toBe(true);
    expect(limiter.tryConsume(0)).toBe(true);
    expect(limiter.tryConsume(0)).toBe(true);
    expect(limiter.tryConsume(0)).toBe(false);
  });

  it("refills continuously rather than on a window boundary", () => {
    const limiter = new RateLimiter(2, 10, 0);
    expect(limiter.tryConsume(0)).toBe(true);
    expect(limiter.tryConsume(0)).toBe(true);
    expect(limiter.tryConsume(0)).toBe(false);
    // 100ms at ten per second is exactly one token back.
    expect(limiter.tryConsume(100)).toBe(true);
    expect(limiter.tryConsume(100)).toBe(false);
  });

  it("never banks more than capacity while idle", () => {
    const limiter = new RateLimiter(2, 10, 0);
    expect(limiter.tryConsume(60_000)).toBe(true);
    expect(limiter.tryConsume(60_000)).toBe(true);
    expect(limiter.tryConsume(60_000)).toBe(false);
  });
});

describe("queue removal", () => {
  const entry = (providerId: string) => ({
    track: validateTrackRef({ ...validTrack, providerId }),
    addedBy: "member-1",
  });

  it("removes one copy of a duplicated track, not both", () => {
    const queue = [entry("a"), entry("b"), entry("a")];
    const next = removeFirstMatch(queue, "a");
    expect(next.map((e) => e.track.providerId)).toEqual(["b", "a"]);
  });

  it("leaves the queue alone when nothing matches", () => {
    const queue = [entry("a")];
    expect(removeFirstMatch(queue, "z")).toBe(queue);
  });

  it("does not mutate the queue it was given", () => {
    const queue = [entry("a"), entry("b")];
    removeFirstMatch(queue, "a");
    expect(queue).toHaveLength(2);
  });
});

describe("ice servers", () => {
  /** Shaped like a real response from the TURN credential API. */
  const minted = {
    iceServers: [
      { urls: ["stun:stun.cloudflare.com:3478"] },
      {
        urls: [
          "turn:turn.cloudflare.com:3478?transport=udp",
          "turn:turn.cloudflare.com:3478?transport=tcp",
          "turn:turn.cloudflare.com:80?transport=tcp",
          "turns:turn.cloudflare.com:5349?transport=tcp",
          "turns:turn.cloudflare.com:443?transport=tcp",
        ],
        username: "a".repeat(96),
        credential: "b".repeat(96),
      },
    ],
  };

  it("keeps TURN over TLS on 443, which is the one that survives a hostile network", () => {
    const servers = normaliseIceServers(minted);
    const urls = servers.flatMap((server) => server.urls);
    expect(urls).toContain("turns:turn.cloudflare.com:443?transport=tcp");
    expect(servers[1]?.username).toBe("a".repeat(96));
    expect(servers[1]?.credential).toBe("b".repeat(96));
  });

  it("drops port 53, which is blocked often enough to only cost a timeout", () => {
    const servers = normaliseIceServers({
      iceServers: [{ urls: ["turn:turn.cloudflare.com:53?transport=udp", "turn:turn.cloudflare.com:3478"] }],
    });
    expect(servers[0]?.urls).toEqual(["turn:turn.cloudflare.com:3478"]);
  });

  it("refuses a URL that is not a STUN or TURN address", () => {
    // Whatever comes out of here is configured straight onto a peer connection.
    const servers = normaliseIceServers({
      iceServers: [{ urls: ["https://example.invalid/steal", "javascript:alert(1)"] }],
    });
    expect(servers).toEqual([PUBLIC_STUN]);
  });

  it("falls back to public STUN rather than handing back nothing", () => {
    expect(normaliseIceServers(null)).toEqual([PUBLIC_STUN]);
    expect(normaliseIceServers({})).toEqual([PUBLIC_STUN]);
    expect(normaliseIceServers({ iceServers: "no" })).toEqual([PUBLIC_STUN]);
    expect(normaliseIceServers({ iceServers: [] })).toEqual([PUBLIC_STUN]);
  });

  it("accepts a single url string as well as a list", () => {
    const servers = normaliseIceServers({ iceServers: [{ urls: "stun:stun.cloudflare.com:3478" }] });
    expect(servers[0]?.urls).toEqual(["stun:stun.cloudflare.com:3478"]);
  });

  it("asking for ice servers is a message with nothing else in it", () => {
    expect(parseClientMessage(JSON.stringify({ type: "ice" }))).toEqual({ type: "ice" });
    expect(() => parseClientMessage(JSON.stringify({ type: "ice", urls: ["turn:evil"] }))).toThrow(
      ValidationError,
    );
  });
});
