/**
 * Wire format for a listen-together session, and the validation that guards it.
 *
 * Deliberately pure: no Workers APIs, no Durable Object, no I/O. The rules below are the security
 * boundary of the whole relay, and they are worth being able to test without spinning up a runtime.
 *
 * The rule that matters most: a message may never carry a playable stream URL. Peers exchange a
 * TrackRef — provider plus id plus enough metadata to display it — and each phone resolves the
 * actual audio locally. That way a compromised relay, or a malicious peer, cannot make someone
 * else's player fetch an arbitrary URL. Validation is allowlist-based for exactly this reason:
 * unknown fields are rejected rather than ignored, so a new field cannot smuggle one in.
 */

export const MAX_MESSAGE_BYTES = 4096;
export const MAX_MEMBERS = 16;
export const MAX_CHAT_HISTORY = 50;
export const MAX_QUEUE_ITEMS = 200;

/** Unambiguous alphabet: no 0/O, no 1/I/L. Codes get read aloud and typed by hand. */
export const CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
export const CODE_LENGTH = 6;

export type Provider =
  | "local"
  | "audius"
  | "jamendo"
  | "youtube"
  | "jiosaavn"
  | "imported";

const PROVIDERS: readonly string[] = [
  "local",
  "audius",
  "jamendo",
  "youtube",
  "jiosaavn",
  "imported",
];

export interface TrackRef {
  provider: Provider;
  providerId: string;
  title: string;
  artist: string;
  durationMs: number;
  /** Cover art only. Never audio, and never used to fetch anything playable. */
  artUrl?: string;
  /** Lets an edition without the sender's catalog find its own copy of the song. */
  fallbackQuery?: string;
}

export type ClientMessage =
  | { type: "ping"; at: number }
  | { type: "playback"; track: TrackRef; positionMs: number; isPlaying: boolean; speed: number }
  | { type: "seek"; positionMs: number }
  | { type: "queueAdd"; track: TrackRef }
  | { type: "queueRemove"; providerId: string }
  | { type: "buffering"; buffering: boolean }
  /** Body is ciphertext. The relay cannot read chat, and does not try. */
  | { type: "chat"; ciphertext: string }
  | { type: "reaction"; emoji: string }
  | { type: "bye" };

export type ServerMessage =
  | { type: "welcome"; memberId: string; hostId: string; serverMs: number; state: RoomSnapshot }
  | { type: "pong"; at: number; serverMs: number }
  | { type: "presence"; members: Member[]; hostId: string; serverMs: number }
  | {
      type: "playback";
      senderId: string;
      serverMs: number;
      track: TrackRef;
      positionMs: number;
      isPlaying: boolean;
      speed: number;
    }
  | { type: "seek"; senderId: string; serverMs: number; positionMs: number }
  | { type: "queue"; serverMs: number; items: QueueEntry[] }
  | { type: "chat"; senderId: string; serverMs: number; ciphertext: string }
  | { type: "reaction"; senderId: string; serverMs: number; emoji: string }
  | { type: "error"; code: ErrorCode; message: string };

export type ErrorCode =
  | "bad_message"
  | "too_large"
  | "rate_limited"
  | "room_full"
  | "unauthorized"
  | "not_found";

export interface Member {
  id: string;
  name: string;
  isHost: boolean;
  buffering: boolean;
  joinedAtMs: number;
  /**
   * IANA zone id, so the other phone can say whether it is the middle of the night where you are.
   * Carried because the whole point of this relay is two people who are not in the same place.
   */
  timeZone: string;
}

export interface QueueEntry {
  track: TrackRef;
  addedBy: string;
}

export interface RoomSnapshot {
  members: Member[];
  hostId: string;
  playback: {
    track: TrackRef;
    positionMs: number;
    isPlaying: boolean;
    speed: number;
    /** Server clock at the moment this position was true. The whole point of a relay. */
    atServerMs: number;
  } | null;
  queue: QueueEntry[];
  chat: { senderId: string; serverMs: number; ciphertext: string }[];
}

export class ValidationError extends Error {
  constructor(
    readonly code: ErrorCode,
    message: string,
  ) {
    super(message);
  }
}

function fail(message: string, code: ErrorCode = "bad_message"): never {
  throw new ValidationError(code, message);
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Rejects unknown keys rather than ignoring them — see the note at the top of this file. */
function allowOnly(value: Record<string, unknown>, keys: readonly string[], what: string): void {
  for (const key of Object.keys(value)) {
    if (!keys.includes(key)) fail(`${what} has an unexpected field "${key}"`);
  }
}

function str(value: unknown, field: string, maxLength: number): string {
  if (typeof value !== "string") fail(`${field} must be a string`);
  if (value.length > maxLength) fail(`${field} is longer than ${maxLength} characters`);
  return value;
}

function finiteNumber(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) fail(`${field} must be a finite number`);
  return value;
}

function bool(value: unknown, field: string): boolean {
  if (typeof value !== "boolean") fail(`${field} must be a boolean`);
  return value;
}

/**
 * Cover art must be an https URL, and is capped in length. It is passed to an image loader, never
 * to the audio player, but a peer should still not be able to point it at an internal address.
 */
function validateArtUrl(value: unknown): string {
  const raw = str(value, "artUrl", 512);
  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    return fail("artUrl is not a valid URL");
  }
  if (parsed.protocol !== "https:") fail("artUrl must be https");
  return raw;
}

export function validateTrackRef(value: unknown): TrackRef {
  if (!isPlainObject(value)) fail("track must be an object");
  allowOnly(
    value,
    ["provider", "providerId", "title", "artist", "durationMs", "artUrl", "fallbackQuery"],
    "track",
  );

  const provider = str(value.provider, "track.provider", 32);
  if (!PROVIDERS.includes(provider)) fail(`track.provider "${provider}" is not a known provider`);

  const durationMs = finiteNumber(value.durationMs, "track.durationMs");
  if (durationMs < 0) fail("track.durationMs must not be negative");

  const track: TrackRef = {
    provider: provider as Provider,
    providerId: str(value.providerId, "track.providerId", 256),
    title: str(value.title, "track.title", 512),
    artist: str(value.artist, "track.artist", 512),
    durationMs,
  };

  if (value.artUrl !== undefined) track.artUrl = validateArtUrl(value.artUrl);
  if (value.fallbackQuery !== undefined) {
    track.fallbackQuery = str(value.fallbackQuery, "track.fallbackQuery", 256);
  }
  if (track.providerId.length === 0) fail("track.providerId must not be empty");

  return track;
}

export function validateClientMessage(value: unknown): ClientMessage {
  if (!isPlainObject(value)) fail("message must be an object");
  const type = str(value.type, "type", 32);

  switch (type) {
    case "ping":
      allowOnly(value, ["type", "at"], "ping");
      return { type: "ping", at: finiteNumber(value.at, "at") };

    case "playback": {
      allowOnly(value, ["type", "track", "positionMs", "isPlaying", "speed"], "playback");
      const positionMs = finiteNumber(value.positionMs, "positionMs");
      if (positionMs < 0) fail("positionMs must not be negative");
      const speed = finiteNumber(value.speed, "speed");
      // Outside this range it is not playback any more, and a huge value would make the
      // receiver's drift maths project a position wildly into the future.
      if (speed < 0.25 || speed > 4) fail("speed must be between 0.25 and 4");
      return {
        type: "playback",
        track: validateTrackRef(value.track),
        positionMs,
        isPlaying: bool(value.isPlaying, "isPlaying"),
        speed,
      };
    }

    case "seek": {
      allowOnly(value, ["type", "positionMs"], "seek");
      const positionMs = finiteNumber(value.positionMs, "positionMs");
      if (positionMs < 0) fail("positionMs must not be negative");
      return { type: "seek", positionMs };
    }

    case "queueAdd":
      allowOnly(value, ["type", "track"], "queueAdd");
      return { type: "queueAdd", track: validateTrackRef(value.track) };

    case "queueRemove":
      allowOnly(value, ["type", "providerId"], "queueRemove");
      return { type: "queueRemove", providerId: str(value.providerId, "providerId", 256) };

    case "buffering":
      allowOnly(value, ["type", "buffering"], "buffering");
      return { type: "buffering", buffering: bool(value.buffering, "buffering") };

    case "chat":
      allowOnly(value, ["type", "ciphertext"], "chat");
      return { type: "chat", ciphertext: str(value.ciphertext, "ciphertext", 2048) };

    case "reaction":
      allowOnly(value, ["type", "emoji"], "reaction");
      return { type: "reaction", emoji: str(value.emoji, "emoji", 16) };

    case "bye":
      allowOnly(value, ["type"], "bye");
      return { type: "bye" };

    default:
      return fail(`unknown message type "${type}"`);
  }
}

/** Parses a raw frame, enforcing the size cap before doing any work on the contents. */
export function parseClientMessage(raw: string): ClientMessage {
  if (raw.length > MAX_MESSAGE_BYTES) {
    throw new ValidationError("too_large", `message exceeds ${MAX_MESSAGE_BYTES} bytes`);
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new ValidationError("bad_message", "message is not valid JSON");
  }
  return validateClientMessage(parsed);
}

export function generateRoomCode(random: () => number = Math.random): string {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) {
    code += CODE_ALPHABET[Math.floor(random() * CODE_ALPHABET.length)];
  }
  return code;
}

export function isValidRoomCode(code: string): boolean {
  if (code.length !== CODE_LENGTH) return false;
  for (const character of code) {
    if (!CODE_ALPHABET.includes(character)) return false;
  }
  return true;
}

/**
 * Token bucket, refilled continuously rather than on a fixed window, so a burst at a window
 * boundary cannot get through at twice the intended rate.
 */
export class RateLimiter {
  private tokens: number;
  private lastRefillMs: number;

  constructor(
    private readonly capacity: number,
    private readonly refillPerSecond: number,
    nowMs: number,
  ) {
    this.tokens = capacity;
    this.lastRefillMs = nowMs;
  }

  tryConsume(nowMs: number): boolean {
    const elapsedSeconds = Math.max(0, nowMs - this.lastRefillMs) / 1000;
    this.tokens = Math.min(this.capacity, this.tokens + elapsedSeconds * this.refillPerSecond);
    this.lastRefillMs = nowMs;
    if (this.tokens < 1) return false;
    this.tokens -= 1;
    return true;
  }
}

/**
 * Removes one copy of a track from the queue, not every copy. The wire format identifies a queue
 * entry by provider id alone, so a queue holding the same song twice would otherwise lose both
 * when someone taps remove on one of them.
 */
export function removeFirstMatch(queue: QueueEntry[], providerId: string): QueueEntry[] {
  const index = queue.findIndex((entry) => entry.track.providerId === providerId);
  if (index === -1) return queue;
  return [...queue.slice(0, index), ...queue.slice(index + 1)];
}
