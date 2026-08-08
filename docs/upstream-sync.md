# Upstream review ledger

MusicHud-TuneWeave reviews the upstream repository's default development branch, currently `upstream/1.21.6-1.21.8`. Release/version branches are not treated as the source of newly developed behavior because upstream normally merges into them later.

## 2026-08-08 review

Reviewed through upstream commit `94661c8694aee7c7dced27a9dd00c9028de01d8f`.

Absorbed as narrow, independently tested ports:

- full-line and word-by-word lyric parsers now expand multiple timestamps before one lyric line;
- signed integer input permits negative HUD offsets;
- music collection subscribe controls no longer dereference a missing profile or appear while logged out.

Already represented locally:

- same-track playback no longer depends on the older context-switch workaround.

Deferred for adaptation in the matching MusicHud-TuneWeave stage:

- virtualized/flex collection UI and cache merging belong with the pagination/entity-cache work;
- large detail-view and background/theme changes need focused visual review instead of commit-level cherry-picks.

Not cherry-picked:

- upstream connection and idle-source commits are coupled to the historical account/protocol design;
- upstream README changes describe the upstream project rather than this independent fork.

The policy is to port behavior and tests when it improves this fork, while keeping TuneWeave architecture and the breaking protocol boundary authoritative.
