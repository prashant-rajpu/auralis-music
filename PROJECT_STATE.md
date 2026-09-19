# Project State

**For the full picture — everything done, everything left, and how to set up a
desktop — see [`docs/HANDOVER.md`](docs/HANDOVER.md).** This file is the
one-screen version.

## Where things are

| | |
|---|---|
| Working branch | `claude/grill-me-c8klg5`, 9 commits ahead of `main`, all pushed, no PR open |
| CI | Green on every commit |
| Tests | 106 on `play`, 113 on `plus` |

## Done

- **Phase 0 — Stabilise** (merged, PR #1). Modern toolchain, signed release
  builds, R8, and the security fixes: no header logging in release, peers can
  no longer inject stream URLs into Together Mode, MediaSession no longer
  accepts every controller, downloads excluded from cloud backup.
- **Phase 1 — Editions** (merged, PR #1). `play` / `plus` flavors with the
  scraped sources compiled only into `plus`, proven on the built APK by
  `scripts/verify-play-flavor.sh`. `MusicSource` / `StreamResolver`
  abstractions; on-device music via MediaStore.
- **Phase 2 (partial)** — the queue became pure, tested logic (fixing three
  real bugs, including a shuffle that picked a random index on every skip);
  resolved stream URLs cached in Room with expiry read from the URL itself.
- **Design pass** — a real theme (light / dark / AMOLED, six accents,
  persisted and applied); the player became a draggable sheet; Home, Explore
  and Library became three different screens instead of one screen filtered
  three ways.
- **v4.0 — the library became real.** Room v4 with eleven new tables and an
  additive, mutation-tested migration. Likes, playlists, play history and the
  queue now survive a force-stop.

## Next

**v4.1 — Together 2.0**, the headline feature, aimed at long-distance
couples. Cloudflare Worker relay for a trustworthy shared clock, ±150 ms
sync with a drift-correction table, shared queue, chat, presence, and the
couple layer (Our Songs, streaks, scheduled sessions, dedications, goodnight
mode). Chat and dedications encrypted client-side so the relay cannot read
them.

Then: showpiece player (artwork-driven colour, waveform scrubber, gestures),
personalization (Daily Mixes, Wrapped, Wrapped For Two), deep customization,
and platform surfaces (widgets, Android Auto).

## Deferred, deliberately

Moving the player into the service — deleting `PlaybackManager` (943 lines)
and rebuilding around `MediaController`. The user-visible result it was meant
to deliver (queue and position surviving a force-stop) already shipped via
`PlaybackStatePersister`, so the rewrite can now happen on its own and be
tested on a real device, rather than carrying that feature behind it.

## Blockers

None. Note that no change in this project has been verified on a real device
— see §6 of the handover for exactly what that does and does not cover.
