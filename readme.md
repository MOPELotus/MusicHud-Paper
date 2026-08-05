![Banner](
https://cdn.modrinth.com/data/7Rnb6oJr/images/20ba5cbcaf71e2ab70436963776e5f801fea44a7.png)
![Home View](https://cdn.modrinth.com/data/7Rnb6oJr/images/b1ab5a9d276489d9f2ef4ecce3fd318e58290553.png)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Etern-34520/MusicHud)

#### A GUI-based, all situations (singleplayer/multiplayers) music mod/plugin designed with zero modifications to game mechanics

> Due to service scope provided by NetEase, this mod might not work well outside China

## Related Links
[ModRinth](https://modrinth.com/mod/music-hud)

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/music-hud)

[Third-party Bukkit plugin 1](https://github.com/Shiroiame-Kusu/MusicHud-Bukkit) (1.0.5 stable -)

[Third-party Bukkit plugin 2](https://github.com/MOPELotus/MusicHud-Paper) (1.1.4 hotfix +, now partly merged into main repository)

## Prerequisites
- ModernUI (only client, using mVUS fork on 1.21.9-11, [this fork](https://github.com/Chino081/ModernUI-MC/releases/tag/26.2) on 26.2)
- Forge Config API Port (only fabric)
- ~~Architectury API~~ (Mod edition, 1.2.0 and below)

## Features
- With graceful GUI, providing an in-game operation interface and a easily configurable HUD.
- Can read NetEase Cloud Music account playlists for convenient song requests.
- The server does not retain user data; it is only temporarily stored during login.
- Streamed playback with no redundant client-side caching.
- Staggered lyrics auto scroll inspired by Apple Music
- HUD dynamic fluid background
- Windows SMTC / Linux MPRIS (have not fully tested yet) support

## Functions
- Search for musics, playlists, albums and artists.
- Log in to NetEase Cloud Music account via QR code or SMS code to explore subscribed playlists, albums and artists.
- Connected mode: Synchronized server-wide play queue.
- Isolated mode: Do all works on client, enjoy music yourself.
- Configure idle playback sources for automatic random song switching when music player is idle.
- Display lyrics, requesting player (or avatar) in the HUD and user interface.

## Compatibility
- Currently specially supports to mute Reactive Music when MusicHUD is playing

## TO-DO List
- Customizable HUD layout [target 1.3.0/1.4.0]
- More music platform support (QQ Music, Kugou Music) [target 2.0.0]

## Usage

### Client
First of all, place the prerequisite mods and the MusicHud jar file into the mods folder.

#### Single-player or LAN Multi-player Host
All you need is to configure or deploy an API server.

#### Multiplayer (join Server or LAN Multi-player)
##### Connected mode
You don't need to configure or deploy an API server, but the server or LAN multiplayer host needs to configure or deploy MusicHUD and API server.

##### Isolated mode
All you need is to configure or deploy an API server.

### Server
All you need is to configure or deploy an API server.

### How to Deploy an API Server
There are 2 methods to deploy.
#### Deploy bound with mod (recommend for client)
> This method allows MusicHUD to manage lifecycle of api server.
>
> Due to some limitations, API Server may not be auto-closed when game exit abnormal (e.g., due to a crash)

##### Auto Deploy
Use **Download TuneWeave...** under API status in the settings page. MusicHUD reads TuneWeave's [release manifest](https://github.com/MOPELotus/TuneWeave/blob/main/release-manifest.json), selects the binary for the current operating system and architecture, optionally routes the download through a GitHub proxy, and verifies the published SHA-256 checksum. After downloading, MusicHUD can set the executable path and start TuneWeave for you.

##### Manual Deploy
1. Open the current TuneWeave [release manifest](https://github.com/MOPELotus/TuneWeave/blob/main/release-manifest.json) or [release page](https://github.com/MOPELotus/TuneWeave/releases).
2. Download the single-file binary matching your operating system and architecture, then verify it with the adjacent `.sha256` file. No archive extraction or GitHub Actions login is required.
3. Place the executable in a private local directory and set `serverApiBinaryExecutablePath` to its relative or absolute path in `/config/music_hud-server.toml`. On Linux and macOS, mark it executable first.

#### Deploy separately (recommend for server)
1. Download and run [TuneWeave](https://github.com/MOPELotus/TuneWeave). Its default address is `http://127.0.0.1:7832`.
2. If TuneWeave listens on another address, set `serverApiBaseUrl` in `/config/music_hud-server.toml` to that base URL.

---
## CN version description

#### 图形化的全场景（单人/多人游戏）音乐模组/插件，设计上不修改任何游戏机制

## 相关链接

[MC 百科](https://www.mcmod.cn/class/23688.html)

[第三方 Bukkit 插件实现 1](https://github.com/Shiroiame-Kusu/MusicHud-Bukkit) (1.0.5 stable -)

[第三方 Bukkit 插件实现 2](https://github.com/MOPELotus/MusicHud-Paper) (1.1.4 hotfix +，目前已部分合并至主仓库)

## 前置依赖
- ModernUI （仅客户端，在 1.21.9 上使用 mVUS 分支，在 26.2 上使用[该分支](https://github.com/Chino081/ModernUI-MC/releases/tag/26.2)）
- Forge Config API Port （仅 Fabric）
- ~~Architectury API~~ （非插件版，1.2.0 以及更低版本）

## 特点
- 具有优雅的 GUI，提供游戏内操作界面，以及易配置的 HUD
- 可读取网易云账户歌单，方便点歌
- 服务端不保留用户数据，仅在登录时暂存
- 流式播放，无赘余的客户端缓存
- 受 Apple Music 启发的歌词交错滚动与逐字歌词
- HUD 动态流体背景
- Windows SMTC / Linux MPRIS（尚未充分测试） 支持

## 功能
- 搜索音乐、歌单、专辑和歌手
- 通过二维码或短信验证码登录网易云账户，查看收藏的歌单、专辑和歌手
- 连接模式：全服同步播放队列
- 隔离模式：在客户端上独立享受音乐
- 配置空闲播放源，播放器空闲时随机切歌
- 在 HUD 和用户界面中展示歌词，点歌玩家（或头像）

## 兼容
- 目前特殊支持在 MusicHUD 播放时静音 Reactive Music

## ~~大饼~~ 待办清单
- 可自定义的 HUD 布局 [目标 1.3.0/1.4.0]
- 更多音乐平台支持(QQ音乐, 酷狗音乐) [目标 2.0.0]

## 使用

### 客户端
首先在 mods 文件夹中放入前置 mod 和 MusicHUD 的 jar 文件

#### 单人模式 或 局域网联机主机
只需要配置/部署 API 服务器

#### 加入服务器 或 加入局域网世界
##### 连接模式
不需要配置/部署 API 服务器

但是需要服务器/局域网主机配置/部署 MusicHUD 和 API 服务器

##### 隔离模式
只需要配置/部署 API 服务器

### 服务端
只需要部署 API 服务器

### 如何部署 API 服务器
两种方法
#### 绑定在 mod 中（推荐客户端使用）
> 这个方法会让 MusicHUD 管理 API 服务器生命周期
>
> 由于一些限制，在游戏非正常退出时（如崩溃）无法自动结束进程

##### 自动部署（客户端，1.2.15+）
在设置页面的 API 状态区域点击“下载 TuneWeave...”。MusicHUD 会读取 TuneWeave 的[发布清单](https://github.com/MOPELotus/TuneWeave/blob/main/release-manifest.json)，按当前操作系统和架构选择二进制文件，可选通过 GitHub 下载代理加速，并校验清单发布的 SHA-256。下载完成后可直接设为可执行文件路径并启动 TuneWeave。

##### 手动部署
1. 打开 TuneWeave 当前的[发布清单](https://github.com/MOPELotus/TuneWeave/blob/main/release-manifest.json)或 [Release 页面](https://github.com/MOPELotus/TuneWeave/releases)。
2. 下载与操作系统和架构匹配的单文件二进制，并使用旁边的 `.sha256` 文件校验；无需解压，也无需登录 GitHub Actions。
3. 将可执行文件放在私有本地目录，在 `/config/music_hud-server.toml` 中把 `serverApiBinaryExecutablePath` 设为对应的相对或绝对路径。Linux 和 macOS 需要先赋予执行权限。

#### 独立部署（推荐服务端使用）
1. 下载并运行 [TuneWeave](https://github.com/MOPELotus/TuneWeave)，默认地址为 `http://127.0.0.1:7832`。
2. 如果 TuneWeave 监听其他地址，在 `/config/music_hud-server.toml` 中将 `serverApiBaseUrl` 改为对应基址。
