# Music Hud
![Static Badge](https://img.shields.io/badge/Java-21-red?style=for-the-badge)
![Static Badge](https://img.shields.io/badge/License-LGPLv3-brightgreen?style=for-the-badge)

## Paper Release Additions
- Added Paper admin commands: `/musichud` and `/music` for status, player info, config, API process control, playback queue/idle source management, decoder probing, and client playtest.
- Replaced the client audio decoder path with LavaPlayer-backed decoding while keeping upstream UI, login, network, and playback changes synced.
- Supported debug/local playback formats include `aac`, `flac`, `m4a`, `mp3`, `ogg`, `opus`, `opus.ogg`, `wav`, and `webm`; `auto`/`generic` probing is available for unknown sources.
- Paper server jars bundle the decoder libraries, so `/musichud test` and `/musichud playtest` can be used directly after installing the matching client mod.

## 本分支增强
- 新增 Paper 管理指令 `/musichud` 与 `/music`，包含状态查询、玩家信息、配置、API 进程、播放队列/空闲播放源管理、解码器探测和客户端 playtest。
- 将客户端解码链路切换为 LavaPlayer，同时保留并同步上游 UI、登录、网络和播放逻辑更新。
- 调试播放/本地播放支持 `aac`、`flac`、`m4a`、`mp3`、`ogg`、`opus`、`opus.ogg`、`wav`、`webm`，未知来源可用 `auto`/`generic` 自动探测。
- Paper 服务端构建会打包解码依赖，安装匹配客户端 mod 后可直接使用 `/musichud test` 和 `/musichud playtest`。

![Banner](https://cdn.modrinth.com/data/7Rnb6oJr/images/82adebdd95d53dd3d97c41ae2f60d24b033c9fb3.png)
![Home View](https://cdn.modrinth.com/data/7Rnb6oJr/images/2d00c770f54f44fc194f46842f5876e307ab54e5.png)

#### A GUI-based Full Server Song Request Mod / Plugin

> Due to service scope provided by Netease, this mod might not working good outside China

## Related Links

[Third-party Bukkit plugin 1](https://github.com/Shiroiame-Kusu/MusicHud-Bukkit) (1.0.5 stable -)

[Third-party Bukkit plugin 2](https://github.com/MOPELotus/MusicHud-Paper) (1.1.4 hotfix +, now partly merged into main repository)

## Prerequisites
- Fabric: Fabric API and Forge Config API Port are required; Mod Menu and Modern UI are recommended.
- NeoForge: Modern UI is required on the client.
- Paper: install the Paper jar on the server and the matching Fabric/NeoForge client mod for HUD playback.

## Features
- GUI-based, providing an in-game operation interface and a relatively aesthetic, easily configurable HUD.
- Can read NetEase Cloud Music account playlists for convenient song requests.
- The server does not retain user data; it is only temporarily stored during login.
- Streamed playback with no redundant client-side caching.
- Staggered lyrics auto scroll inspired by Apple Music
- HUD dynamic fluid background

## Functions
- Search for music.
- Log in to NetEase Cloud Music account via QR code to request songs from user playlists.
- Synchronized server-wide playlist.
- Configure playlists as idle playback sources for automatic random song switching.
- Display lyrics, requesting player (with avatar) in the HUD and user interface.
- Display the playlist in the user interface.

## Usage

### Client
First of all, place the MusicHud jar file into the `mods` folder. Fabric also requires Fabric API and Forge Config API Port; Mod Menu and Modern UI are recommended. NeoForge requires Modern UI on the client.

#### Single-player or LAN Multi-player Host
> Currently experimentally supports single-player mode.

All you need is to deploy an API server

#### Multiplayer (join Server or LAN Multi-player)
You don't need to deploy an API server, but the server or LAN multiplayer host to deploy MusicHUD and API server

### Server
All you need is to deploy an API server

### How to Deploy an API server
There are 2 methods to deploy
#### Deploy bound with mod (recommend for client)
> This method will allows MusicHUD to manage lifecycle by itself
>
> Due to some limitations, API Server may not be auto-closed when game exit abnormal as crashes

1. **Login to GitHub** and goes to https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/actions/workflows/build-and-pr.yml
2. Click latest action runs in type "build-and-pr" and download platform binary artifacts matches to your pc/server
3. Uncompress the binary executable file from ZIP file to {corepath}/music-hud/ and renames to `api` (`api.exe` on Windows)

> Default path for plugin versions is `{corepath}/api(.exe)`
> Or place it in anywhere and modify config `startupBinaryApiServerWhenLaunch` in game or in config file `music_hud-server.toml`

#### Deploy seperately (recommend for server)
1. Deploy Netease Cloud Music API Enhanced (https://github.com/neteasecloudmusicapienhanced/api-enhanced).
2. If not using the default port (3000) of NCM API Enhanced or deploying on another server, modify the `serverApiBaseUrl` property in the config file.
> Configuration file location: `/config/music_hud-server.toml`

---
## CN version description

#### 一个 GUI 化的全服点歌模组/插件

## 相关链接

[MC 百科](https://www.mcmod.cn/class/23688.html)

[第三方bukkit插件实现 1](https://github.com/Shiroiame-Kusu/MusicHud-Bukkit) (1.0.5 stable -)

[第三方bukkit插件实现 2](https://github.com/MOPELotus/MusicHud-Paper) (1.1.4 hotfix +,，目前已部分合并至主仓库)

## 前置依赖
- Fabric：需要 Fabric API 与 Forge Config API Port，推荐安装 Mod Menu 与 Modern UI。
- NeoForge：客户端需要 Modern UI。
- Paper：服务端安装 Paper 插件 jar，客户端安装匹配的 Fabric/NeoForge mod 才能显示 HUD 并播放。

## 特点
- GUI 化， 提供游戏内操作界面，以及较为美观易配置的 HUD
- 可读取网易云账户歌单，方便点歌
- 服务端不保留用户数据，仅在登录时暂存
- 流式播放，无赘余的客户端缓存
- 受 Apple Music 启发的歌词交错滚动
- HUD 动态流体背景

## 功能
- 搜索音乐
- 通过二维码登录网易云账户，从用户歌单点歌
- 全服同步播放列表
- 配置歌单作为空闲播放源，服务器随机切歌，解放双手（？）
- 在 HUD 和用户界面中展示歌词，点歌玩家（/头像）
- 在用户界面中展示播放列表

## 使用
### 客户端
首先在 `mods` 文件夹中放入 MusicHud 的 jar 文件。Fabric 还需要 Fabric API 与 Forge Config API Port，推荐安装 Mod Menu 与 Modern UI；NeoForge 客户端需要 Modern UI。
#### 单人模式 或 局域网联机主机
> 目前对单人游戏和局域网联机主机提供实验性支持
>
> 由于一些限制，在游戏非正常退出时（如崩溃）无法自动结束进程

只需要部署 API 服务器

#### 加入服务器 或 加入局域网世界
不需要部署 API 服务器但是需要服务器/局域网主机部署 MusicHUD 和 API 服务器

### 服务端
只需要部署 API 服务器

### 如何部署
两种方法
#### 绑定在 mod 中（推荐客户端使用）
> 这个方法会让 MusicHUD 管理 API 服务器生命周期

1. **登录到 GitHub** 并跳转到 https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/actions/workflows/build-and-pr.yml
2. 在 actions 类型中选择 "build-and-pr" 并进入最新的构建中根据你的平台下载对应的二进制构建产物
3. 解压下载得到的压缩包并将其中的二进制可执行文件放置到 {核心目录}/music-hud/ and renames to `api` (`api.exe` on Windows)

> 对应插件版本则是 {核心目录}/api(.exe)
> 或者放在任意处并在游戏内或配置文件中修改选项 `serverApiBinaryExecutablePath` 对应目录

#### 独立部署（推荐服务端使用）
1. 部署 Netease Cloud Music API Enhanced (https://github.com/neteasecloudmusicapienhanced/api-enhanced)
2. 如果不使用 NCM API Enhanced 的默认端口 ( 3000 ) 或在其他服务器上部署，需要修改配置文件的 serverApiBaseUrl 属性
> 配置文件位置 `/config/music_hud-server.toml`
