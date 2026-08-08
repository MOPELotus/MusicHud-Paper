# MusicHud-TuneWeave

MusicHud TuneWeave 是一个由 TuneWeave 驱动的 Minecraft 音乐播放与同步项目，支持游戏内点歌、播放队列、歌词、HUD、隔离播放与多人同步。

> MusicHud TuneWeave is an independent fork of [MusicHud](https://github.com/Etern-34520/MusicHud). It originated from the MusicHud codebase and the former MusicHud-Paper implementation, but has since substantially diverged in music-service architecture, server integration, synchronization, automation, and development direction. It is not an official MusicHud release and does not guarantee API or protocol compatibility with upstream MusicHud.

## 当前支持范围

本分支面向 Minecraft 26.2 / Java 25，当前可直接构建：

- Fabric 客户端模组
- NeoForge 客户端模组

MusicHud TuneWeave 的完整平台目标还包括 Paper 与 Velocity。它们属于跨 Minecraft 客户端版本的服务端部署平台，不作为 Fabric/NeoForge 之外的“客户端 loader”建模。服务端模块合并到正式主线前，以各自的平台分支为准。

## 架构

```text
Minecraft UI / HUD / Queue / Playback / Sync
                        │
                        ▼
             Stable TuneWeave contract
                        │
                        ▼
                    TuneWeave
                        │
              Netease / QQ / Bilibili / ...
```

Minecraft 层只处理游戏集成、播放与同步。音乐平台 endpoint、账号模型、字段差异和 provider fallback 属于 TuneWeave 服务边界，不应重新进入 Minecraft core。

当前模块：

```text
processor  annotation processor for auto-registration
core       platform-independent protocol, domain beans and services
common     shared Minecraft client/UI/audio implementation
fabric     Fabric integration and packaging
neoforge   NeoForge integration and packaging
```

更完整的边界说明见 [架构文档](docs/architecture.md) 和 [TuneWeave contract](docs/tuneweave-contract.md)。

## Privacy

- 音乐平台 credential/session 由客户端持有。
- 默认 UI 只开放二维码和验证码登录；账号密码登录入口当前被统一 feature gate 隐藏并阻断。
- credential、Cookie、token 和密码不得写入 Minecraft Server state、网络日志或普通运行日志。
- Minecraft Server 只应协调播放与同步所需的数据，不应成为通用音乐平台任务调度器。

## 客户端安装

1. 安装与 Minecraft 版本和 loader 对应的 ModernUI。
2. 将 `musichud-tuneweave-fabric-<version>+<mc-version>.jar` 或 `musichud-tuneweave-neoforge-<version>+<mc-version>.jar` 放入 `mods` 目录。
3. 在游戏内设置页配置 TuneWeave。

Fabric 还需要 Fabric API；Mod Menu 为推荐依赖。具体版本以平台 metadata 和当前分支的 `gradle.properties` 为准。

## TuneWeave

### 自动部署

在设置页的 API 状态区域选择“下载 TuneWeave”。客户端会读取 TuneWeave [release manifest](https://github.com/MOPELotus/TuneWeave/blob/main/release-manifest.json)，选择当前系统与架构对应的单文件二进制，并校验 SHA-256。

新版本成功启动并通过健康检查后，MusicHud TuneWeave 会删除安装清单中已经不再使用的旧 managed binary。未列入清单的用户文件不会被删除；删除失败会保留记录并在后续健康启动时重试。

### 独立部署

1. 从 [TuneWeave Releases](https://github.com/MOPELotus/TuneWeave/releases) 下载并运行对应二进制；默认地址为 `http://127.0.0.1:7832`。
2. 在设置中将 `serverApiBaseUrl` 指向该实例。

## 构建与测试

```powershell
.\gradlew.bat common:test
.\gradlew.bat fabric:build
.\gradlew.bat neoforge:build
```

TuneWeave 网络/下载集成测试默认不运行；显式执行：

```powershell
.\gradlew.bat common:test -PintegrationTests
```

开发约束和完整验证方式见 [development.md](docs/development.md)。产物命名与发布边界见 [release.md](docs/release.md)。

## 标识与迁移

本批次已完成项目展示名、Gradle 项目名、构建产物名、日志名与 loader metadata 的独立化。Java package 暂时保留 `indi.etern.musichud.*`；mod ID、网络 namespace 和既有配置/持久化路径需要与协议及迁移测试一起原子调整，详见 [protocol.md](docs/protocol.md)。在该迁移完成前，不支持与 upstream MusicHud 同时安装。

## License 与 attribution

本项目继续遵循仓库中的 LGPL-3.0 许可证，并保留原项目作者与历史贡献的 attribution。

- Upstream: [Etern-34520/MusicHud](https://github.com/Etern-34520/MusicHud)
- Music service: [MOPELotus/TuneWeave](https://github.com/MOPELotus/TuneWeave)
