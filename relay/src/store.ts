import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import type { ChatEntry, QueueEntry, TrackRef } from "./protocol.js";

export interface Playback {
  track: TrackRef;
  positionMs: number;
  isPlaying: boolean;
  speed: number;
  atServerMs: number;
}

/**
 * Everything about a room that has to survive the process restarting.
 *
 * Live sockets are deliberately not in here. Presence is derived from who is connected right now,
 * and a member list rebuilt from disk after a restart would be a list of people who are not there.
 */
export interface RoomRecord {
  code: string;
  hostToken: string;
  hostId: string;
  createdAtMs: number;
  lastSeenMs: number;
  playback: Playback | null;
  queue: QueueEntry[];
  chat: ChatEntry[];
}

/**
 * Rooms, kept in memory and written to one JSON file.
 *
 * A database would be the obvious reach, and it would be the wrong one. The entire state of this
 * service is a handful of rooms holding at most fifty short messages each; it fits in memory
 * several thousand times over, and a file it rewrites every couple of seconds is both faster than
 * a query and impossible to get subtly wrong.
 *
 * The write is debounced and atomic — a temporary file renamed over the real one — so a process
 * killed mid-write leaves the previous version intact rather than half of the new one.
 *
 * Pass `null` as the directory to keep everything in memory. That is the right setting on a host
 * with no persistent disk, where a file would only give the false impression of durability.
 */
export class RoomStore {
  private readonly rooms = new Map<string, RoomRecord>();
  private readonly file: string | null;
  private timer: NodeJS.Timeout | null = null;
  private dirty = false;

  constructor(
    directory: string | null,
    private readonly ttlMs: number,
    private readonly writeDelayMs = 2_000,
  ) {
    this.file = directory === null ? null : join(directory, "rooms.json");
    this.load();
  }

  get size(): number {
    return this.rooms.size;
  }

  get(code: string): RoomRecord | undefined {
    return this.rooms.get(code);
  }

  /**
   * Refuses a code that is already taken rather than overwriting it. A collision must never be
   * able to hand a live couple's room to a stranger; the caller retries with a fresh code.
   */
  create(code: string, hostToken: string, nowMs: number): RoomRecord | null {
    if (this.rooms.has(code)) return null;
    const record: RoomRecord = {
      code,
      hostToken,
      hostId: "",
      createdAtMs: nowMs,
      lastSeenMs: nowMs,
      playback: null,
      queue: [],
      chat: [],
    };
    this.rooms.set(code, record);
    this.touch();
    return record;
  }

  /** Marks the in-memory state as needing a write. Records are mutated in place. */
  touch(): void {
    this.dirty = true;
    if (this.file === null || this.timer !== null) return;
    this.timer = setTimeout(() => {
      this.timer = null;
      this.flush();
    }, this.writeDelayMs);
    // A pending write must never be the reason the process stays alive.
    this.timer.unref?.();
  }

  /** Drops rooms nobody has touched for the TTL. Returns how many went. */
  sweep(nowMs: number): number {
    let removed = 0;
    for (const [code, record] of this.rooms) {
      if (nowMs - record.lastSeenMs < this.ttlMs) continue;
      this.rooms.delete(code);
      removed++;
    }
    if (removed > 0) this.touch();
    return removed;
  }

  flush(): void {
    if (this.file === null || !this.dirty) return;
    this.dirty = false;
    const payload = JSON.stringify({ rooms: [...this.rooms.values()] });
    try {
      mkdirSync(dirname(this.file), { recursive: true });
      const temporary = `${this.file}.tmp`;
      writeFileSync(temporary, payload, "utf8");
      renameSync(temporary, this.file);
    } catch (error) {
      // Losing the snapshot costs the room codes on the next restart, which is a great deal
      // better than taking down a live session over a full disk.
      console.error("could not write rooms", error);
    }
  }

  close(): void {
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = null;
    this.flush();
  }

  private load(): void {
    if (this.file === null) return;
    let raw: string;
    try {
      raw = readFileSync(this.file, "utf8");
    } catch {
      return; // First run, or no disk. Both are ordinary.
    }
    try {
      const parsed = JSON.parse(raw) as { rooms?: RoomRecord[] };
      for (const record of parsed.rooms ?? []) {
        if (typeof record?.code === "string") {
          // Nobody is connected to anything yet, whatever the file says.
          this.rooms.set(record.code, { ...record, hostId: "" });
        }
      }
    } catch (error) {
      console.error("rooms file is not readable; starting empty", error);
    }
  }
}
