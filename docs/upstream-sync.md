# Upstream review ledger

## 2026-09-09 incremental implementation audit

Fetched upstream/1.21.6-1.21.8 and upstream/plugin-wide-range before the workload-ordered batches. No new commits relative to the last reviewed refs: b8ca4ae8cc6088a5783630d5a720abbd25a3d098 and 56ffa9bc436bdcf08ffe94600063c4a3ad74921b. Existing unimplemented items remain in their assigned roadmap stages. The local TuneWeave api-v1.md confirms the scrobble_write capability and actual-listening-duration contract; this was a source inspection, not a live account submission.

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

## 2026-09-08 implementation batch 1: NeoForge payload delivery

Ported the payload-unwrapping fix from upstream `3d8698c7`. NeoForge's C2S and S2C callbacks previously tested the Minecraft transport envelope against `IPayload`; the envelope does not implement that interface, so both callbacks skipped the domain receiver. Both callbacks now use a typed wrapper delivery method that passes the inner payload and player to the registered receiver. Fabric already unwraps its packets.

Validation: three regression tests round-trip real C2S/S2C messages through `StreamCodecWrapper`, verify delivery and player/session identity, and reject a truncated C2S message before delivery. The targeted tests and NeoForge compilation passed, followed by `common:test fabric:build neoforge:build --rerun-tasks`. Review found no protocol, credential, or playback-state changes. The final forced rerun also passed: 23 executed tasks, 43 common tests with zero failures/errors/skips, and both loader builds successful. Opt-in network integration tests were not enabled.

This completes only the identified NeoForge dispatch defect. The non-singleplayer synchronization failure has not yet been reproduced in a two-client runtime after this patch; LAN was the observed case, while independent-server synchronization remains untested as the same class of scenario. Handshake/initial-state ordering, reconnect, late join, resolver fallback, and the Fabric/NeoForge runtime matrix remain open. Local loopback bypasses these NeoForge callbacks, so singleplayer success does not validate this path.

The broader August 8 onward review covers upstream main through `40836cc3` and plugin-wide-range through `56ffa9bc`; reviewed candidates are not all implemented. Animation/subpixel rendering, Float32 audio, other UI/audio/platform changes and cross-version propagation remain planned. The fork boundary is music-provider APIs and affected behavior; unrelated upstream refactors are eligible for adoption when compatible with the local contracts.

TuneWeave `v0.1.0-alpha.9` remains the next integration baseline. Scrobble/check-in is deferred: no corresponding write route was found in the inspected alpha.9 contract. Do not substitute history reads or implement provider calls in Minecraft. Cross-version propagation precedes the final BungeeCord stage. The six-hour upstream-audit requirement is documented locally; no background automation was enabled.


The current audit batch also ports the upstream initial-state stale-result idea: `ConnectionManager` now binds initial-state application to the connection generation and rejects responses from a prior disconnect/reconnect or mode. `common:test --rerun-tasks` passed; runtime non-singleplayer verification remains open.

The current sync audit also closes a connect-order race: compatible players now enter `ServerPlayerRegistry` before ConnectResponse is sent, so the first client operation cannot race ahead of broadcast membership registration. `common:test --rerun-tasks` passed.

The phase audit confirmed two sealing items were already implemented: `LoginFeaturePolicy` hides password login and rejects the direct route by default, with regression tests; `ApiBinaryUpdateService` performs manifest-scoped obsolete-binary cleanup with rollback/path-safety tests. `HomeView` uses compare-and-set active-instance detachment, so late old detach cannot clear a newer view; a dedicated lifecycle timing regression test is still pending.

Static broadcast audit found queue add/remove, playback switch/stop, idle-source updates, and resource refresh all route through `sendToPlayers(playerRegistry.players(), ...)`; C2S handlers enter `MusicPlayerServerService`. No clear memory-only update was found. Runtime member registration, platform callbacks, and dedicated-server behavior still require non-singleplayer testing.

The phase registration audit confirmed all public queue/playback/idle-source/resolve messages have direction-appropriate common registrations; client receivers are installed on the client side and server handlers on the server side. No isolated-mode-only registration condition was found.

To make the non-singleplayer failure observable, Fabric and NeoForge S2C senders now warn with payload, UUID, and target type when a registered player cannot be converted to a server player instead of silently dropping the packet. Full forced common/Fabric/NeoForge validation passed.

The sync diagnostic batch now logs protocol player join/leave with UUID, client type, and registry size, making membership loss observable during LAN/dedicated-server testing. `common:test --rerun-tasks` passed.

2026-09-08 continuation audit: fetched both upstream branches; no new commits since the previous review. SHAs remain `40836cc3` and `56ffa9b`. No new absorption item was available to schedule.

The client C2S path now warns when a non-connect payload is dropped because the connection is not established and isolated mode is disabled, exposing another previously silent cause of no-op multiplayer actions. Full forced validation passed.

Handshake diagnostics now record player UUID, compatibility, runtime side, response acceptance, and registry size at response time. `common:test --rerun-tasks` passed.

The connection handshake fallback timeout is now 5 seconds instead of 1, reducing false isolated-mode fallback during LAN/dedicated-server startup; connection-generation guards still invalidate stale fallbacks. Full forced validation passed.

Absorbed the minimal compatible part of upstream `3a1888ef`: `LyricLine.HighlightSpan` now applies a near-identity perspective matrix (`persp0=1e-8f`) before drawing so Arc3D uses the transformed-mask path and preserves fractional animation positions. The local span animation implementation remains; the upstream lyric rewrite was not copied. `common:test --rerun-tasks` passed; visual runtime checks remain open.

Upstream Float32 audio (`44724ea3`) remains scheduled for platform/audio phase 5. The local branch lacks upstream's `PlaybackTask`, `WavStreamDecoder`, and `FLACStreamDecoder`, so the feature must be reimplemented against the local `StreamAudioPlayer`/decoder/OpenAL path rather than cherry-picked; it is explicitly tracked as pending local-pipeline implementation.

Initial-state acquisition now retries up to three attempts and checks mode, connection generation, and external connected status before each attempt, preventing a transient packet loss from leaving a connected client without public state. `common:test --rerun-tasks` passed.

Request/response sending now installs timeout cleanup before dispatch and removes the pending correlation when transport dispatch throws immediately, preventing handshake or initial-state requests from hanging indefinitely. `common:test --rerun-tasks` passed.

Full forced validation after the current synchronization batch passed: `common:test fabric:build neoforge:build --rerun-tasks` (23 tasks executed). Non-singleplayer runtime verification remains open.

2026-09-08 roadmap continuation: re-fetched upstream/1.21.6-1.21.8 and upstream/plugin-wide-range; SHAs unchanged (40836cc3 / 56ffa9bc). No new feature item. Existing scheduled items remain assigned to phases 3-6 and must be implemented or explicitly blocked; the current local Float32 pipeline mismatch is tracked as a phase-5 implementation prerequisite.

## 2026-09-09 ModernUI fork 依赖同步

- 已 fetch https://github.com/MOPELotus/ModernUI-MC，HEAD / origin/master / 26.2-3.13.0.7 标签均为 0295ab8a37a6bbff87e730e5ea1fd3794b058b85。
- fork 两个 loader 强制构建通过（24 tasks executed）；MusicHud 改用公开 GitHub Release 固定版本 universal JAR 的 Gradle Ivy 依赖，移除相邻 checkout / local_modernui_dir 依赖。Actions 无需另行构建 ModernUI。
- common:test fabric:build neoforge:build --rerun-tasks 使用公开下载依赖通过（23 tasks executed），日志 .codex-local/verify-2026-09-09-modernui-public-release.log。未运行 GitHub 托管 runner 或实机视觉验收。

## 2026-09-10 上游跟踪

fetch upstream 成功，upstream/1.21.11 从 479a4e3f 前进至合并提交 71db6ba5。差异涉及已跟踪的 b8ca4ae8 HUD/取色，以及旧服务端加载异常的曲名/ID 提取。当前公共会话分支的错误报告需按本地会话模型另行核对，未直接合并旧播放器。该上游跟踪不代表新增实机验收证据。
