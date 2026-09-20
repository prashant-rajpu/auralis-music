# Working on Auralis

Start with [`docs/HANDOVER.md`](docs/HANDOVER.md) — it covers what exists,
what is left, and what has and has not been verified.

## Build and verify

```bash
./gradlew assemblePlusDebug          # sideload edition, all sources
./gradlew assemblePlayDebug          # Play-Store-safe edition
```

Run this before every commit:

```bash
./gradlew testPlayDebugUnitTest testPlusDebugUnitTest \
          assemblePlayRelease assemblePlusRelease lint
ANDROID_HOME=$ANDROID_HOME ./scripts/verify-play-flavor.sh
```

The last script is the one that matters for Play compliance: it inspects the
built `play` APK for scraped-source packages rather than trusting the source
layout.

`relay/` is a separate Node service with its own toolchain, and the Gradle
build does not touch it. If you changed anything under `relay/`:

```bash
cd relay && npm test && npm run typecheck && npm run build
DATA_DIR="" npm start &                   # then, in the same shell
npm run smoke -- http://localhost:8787
```

The smoke test is the one that catches what the unit tests cannot, and it runs
against a local process now rather than needing a deployment.

See [`docs/RELAY.md`](docs/RELAY.md) for what it is and how it deploys.

## Things that will bite you

1. **AGP 9 has Kotlin built in.** Do not apply `org.jetbrains.kotlin.android`
   — the build fails if you do.
2. **Never hand-write migration SQL** in `data/local/Migrations.kt`. Room
   aborts on open if a migrated table differs from its exported schema by so
   much as a column order, which bricks every existing install. Change
   entities, build so KSP writes `app/schemas/…/N.json`, then generate the
   statements from that schema. `MigrationSqlTest` fails if the two drift.
3. **There is no destructive migration fallback**, deliberately. A schema
   change without a real migration means the app will not open.
4. **`app/schemas/` is committed on purpose** — the migration tests read it.
5. **The foreground media service must post a notification within ~10 s** of
   `startForegroundService()`, and Media3 only does so once playback actually
   starts. Never start it speculatively; see `stopMediaServiceIfIdle`.
6. **Secrets never enter the repo.** `keystore.properties`, `*.jks` and
   `local.properties` are gitignored. The Jamendo client id comes from
   `auralis.jamendoClientId` in `~/.gradle/gradle.properties` or the
   `JAMENDO_CLIENT_ID` CI secret.

## Conventions

- Two flavors: `play` (no scraped sources) and `plus` (adds YouTube Music and
  JioSaavn, `applicationIdSuffix ".plus"`). Anything scraped lives in
  `app/src/plus/` and must never reach `play`.
- UI reads colours from the semantic tokens in `ui/theme/`, never raw hex.
- Screens and view models talk to `LibraryRepository`, not to DAOs.
- Room DAOs return `Flow`; writes are `suspend`.
