/**
 * Working out who is on the other end, behind whatever proxy the host puts in front.
 *
 * `X-Forwarded-For` is a list the client gets to start: a caller can send one, and every proxy
 * after that appends what it actually saw. So the leftmost entry — the one everybody reaches for
 * — is the single value a caller controls, which makes it exactly the wrong one to rate-limit on.
 * Counting from the right instead lands on the address a proxy we trust observed, and a forged
 * prefix is simply ignored.
 *
 * `hops` is how many proxies stand between the caller and this process: 1 on a normal hosting
 * platform, 0 when nothing is in front, in which case the header is not consulted at all.
 */
export function clientIpFrom(
  forwarded: string | string[] | undefined,
  socketAddress: string | undefined,
  hops: number,
): string {
  const fallback = socketAddress || "unknown";
  if (hops <= 0) return fallback;

  const header = Array.isArray(forwarded) ? forwarded.join(",") : forwarded;
  const entries = (header ?? "")
    .split(",")
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
  if (entries.length === 0) return fallback;

  // One short of the hop count means the chain is shorter than configured — take the leftmost
  // rather than running off the start of the list.
  const index = Math.max(0, entries.length - hops);
  return entries[index] ?? entries[0] ?? fallback;
}
