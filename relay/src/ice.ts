import { createHmac } from "node:crypto";
import { normaliseIceServers, type IceServer } from "./protocol.js";

/**
 * Where a call is told to look for a path between two phones, and how the credentials for it are
 * produced.
 *
 * All of it comes from the environment so the relay is not tied to one TURN provider. Three shapes
 * are supported, which between them cover every option worth having:
 *
 *   - **Nothing configured.** STUN only. Works whenever the two networks will let the phones talk
 *     directly, and fails on the ones that will not.
 *   - **A static username and password** (`TURN_USERNAME` / `TURN_CREDENTIAL`), which is what a
 *     hosted TURN provider hands you.
 *   - **A shared secret** (`TURN_SECRET`), which is coturn's `use-auth-secret` scheme: the relay
 *     derives a credential that expires, so nothing long-lived ever reaches a phone. This is the
 *     one to use when running your own TURN server.
 */
export interface IceConfig {
  stunUrls: string[];
  turnUrls: string[];
  username?: string;
  credential?: string;
  secret?: string;
  ttlSeconds: number;
}

/** Public, free, and not tied to whoever is hosting this relay. */
export const DEFAULT_STUN = ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"];

/** Twelve hours: far longer than any call, so a credential fetched on join still works later. */
export const DEFAULT_TTL_SECONDS = 12 * 60 * 60;

function list(value: string | undefined): string[] {
  return (value ?? "")
    .split(",")
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
}

export function iceConfigFromEnv(env: Record<string, string | undefined>): IceConfig {
  const stun = list(env.STUN_URLS);
  const ttl = Number.parseInt(env.TURN_TTL_SECONDS ?? "", 10);
  return {
    stunUrls: stun.length > 0 ? stun : DEFAULT_STUN,
    turnUrls: list(env.TURN_URLS),
    username: env.TURN_USERNAME || undefined,
    credential: env.TURN_CREDENTIAL || undefined,
    secret: env.TURN_SECRET || undefined,
    ttlSeconds: Number.isFinite(ttl) && ttl > 0 ? ttl : DEFAULT_TTL_SECONDS,
  };
}

/**
 * coturn's REST scheme, and the one every self-hosted TURN server understands.
 *
 * The username carries its own expiry, and the password is an HMAC of it under a secret only the
 * relay and the TURN server know. Nothing is stored and nothing has to be revoked: the credential
 * simply stops working.
 */
export function restCredential(
  secret: string,
  expiresAtSeconds: number,
  label = "auralis",
): { username: string; credential: string } {
  const username = `${Math.floor(expiresAtSeconds)}:${label}`;
  return {
    username,
    credential: createHmac("sha1", secret).update(username).digest("base64"),
  };
}

/**
 * The list handed to a phone.
 *
 * Passed through the same validation the rest of the protocol uses, because a typo in an
 * environment variable ends up configured directly on a peer connection.
 */
export function mintIceServers(
  config: IceConfig,
  nowMs: number,
): { iceServers: IceServer[]; expiresAtMs: number } {
  const servers: { urls: string[]; username?: string; credential?: string }[] = [];
  if (config.stunUrls.length > 0) servers.push({ urls: config.stunUrls });

  let expiresAtMs = 0;
  if (config.turnUrls.length > 0) {
    if (config.secret) {
      const expiresAtSeconds = Math.floor(nowMs / 1000) + config.ttlSeconds;
      const { username, credential } = restCredential(config.secret, expiresAtSeconds);
      servers.push({ urls: config.turnUrls, username, credential });
      expiresAtMs = expiresAtSeconds * 1000;
    } else if (config.username && config.credential) {
      servers.push({
        urls: config.turnUrls,
        username: config.username,
        credential: config.credential,
      });
    } else {
      // URLs with no way to authenticate are worse than none: every one of them is a connection
      // attempt that gets refused, and the call waits for all of them before giving up.
      console.warn("TURN_URLS is set but no credentials are; ignoring it");
    }
  }

  return { iceServers: normaliseIceServers({ iceServers: servers }), expiresAtMs };
}
