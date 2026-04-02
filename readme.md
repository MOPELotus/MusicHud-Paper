# Music Hud
![Static Badge](https://img.shields.io/badge/Java-21-red?style=for-the-badge)
![Static Badge](https://img.shields.io/badge/Minecraft-1.21.x-blue?style=for-the-badge)
![Static Badge](https://img.shields.io/badge/Platform-Fabric-green?style=for-the-badge)
![Static Badge](https://img.shields.io/badge/Platform-Neoforge-orange?style=for-the-badge)
![Static Badge](https://img.shields.io/badge/Platform-Paper-grey?style=for-the-badge)
![Static Badge](https://img.shields.io/badge/License-LGPLv3-brightgreen?style=for-the-badge)

#### 一个带 Paper 服务端增强支持的全服点歌模组维护分支

> 本分支新增了 `/musichud` 与 `/music` 服务端管理指令，并将客户端与服务端的音频解码统一切换为 LavaPlayer。  
> 常见 `mp3`、`flac`、`wav`、`ogg`、`opus`、`aac`、`m4a/mp4`、`aiff`、`au` 现已纳入支持范围，且可通过 `test` / `playtest` 指令直接验证解码与播放链路。

## 相关链接
[ModRinth](https://modrinth.com/mod/music-hud)

[MC 百科](https://www.mcmod.cn/class/23688.html)

[原第三方 Bukkit 插件实现](https://github.com/Shiroiame-Kusu/MusicHud-Bukkit)

![img](https://cdn-alt.modrinth.com/data/7Rnb6oJr/images/96714cbb6621950e3daceee1ab2f7343836e9bbb.png)
![img](https://cdn-alt.modrinth.com/data/7Rnb6oJr/images/49fdedf9f26ec05930035f9f79ba388502f0f62d.png)

## 前置依赖
- ModernUI
- Forge Config API Port
- Architectury API

## 特点
- GUI 化， 提供游戏内操作界面，以及较为美观易配置的 HUD
- 可读取网易云账户歌单，方便点歌
- 服务端不保留用户数据，仅在登录时暂存
- 流式播放，无赘余的客户端缓存
- 增强了 Paper 服务端管理能力，可直接在游戏内或控制台维护配置、播放和 API 状态
- 客户端与服务端播放链统一使用 LavaPlayer，格式兼容性显著提升

## 功能 
- 搜索音乐
- 通过二维码登录网易云账户，从用户歌单点歌
- 全服同步播放列表
- 配置歌单作为空闲播放源，服务器随机切歌，解放双手（？）
- 在 HUD 和用户界面中展示歌词，点歌玩家（/头像）
- 在用户界面中展示播放列表

## 本分支新增
- 新增 Paper 服务端管理指令：`/musichud`、`/music`
- 新增服务端解码探针：`/musichud test <服务器路径或URL> [declaredFormat]`
- 新增客户端调试播放：`/musichud playtest <客户端路径或URL> [declaredFormat]`
- 客户端与服务端解码统一切换为 LavaPlayer
- 统一支持和识别常见音频格式：`mp3`、`flac`、`wav`、`ogg`、`opus`、`aac`、`m4a/mp4`、`aiff`、`au`

## 兼容性说明
- 具体适用的 Minecraft 小版本请以当前分支名和构建产物文件名为准；本 README 统一使用 `1.21.x` 表示同一套维护线。
- 发布时建议将 **Paper 插件** 与本仓库对应版本的 **Fabric/NeoForge 客户端模组** 一起分发与使用。
- 上游旧客户端在部分传统场景下可能仍能处理 `mp3` / `flac`，但本分支新增了新的 `FormatType`、新的调试播放消息以及新的 LavaPlayer 解码链路，**不再保证与上游旧客户端完全兼容**。
- 如果你要使用新增格式支持、`test` / `playtest` 调试能力，或避免 `WAV/OGG/AAC/M4A/OPUS` 等资源在旧客户端上因枚举不一致而失败，请使用本仓库配套客户端。

## Paper 服务端指令
- `/musichud` 或 `/music`：打开帮助
- `/musichud status`：查看服务端、API 与播放状态
- `/musichud player <玩家名>`：查看指定玩家的登录与排队信息
- `/musichud config show|set|save|reload`：查看、修改、保存或重载服务端配置
- `/musichud api status|restart|stop`：管理内置 API 进程
- `/musichud playback skip`：强制切歌
- `/musichud playback queue [remove <索引>]`：查看或移除播放队列
- `/musichud playback idle [remove <玩家> <playlist|album> <ID>]`：管理空闲播放源
- `/musichud test <服务器路径或URL> [declaredFormat]`：只测试服务端解码器与探针信息
- `/musichud playtest <客户端路径或URL> [declaredFormat]`：让自己客户端进行调试播放
- `/musichud playtest <玩家> <客户端路径或URL> [declaredFormat]`：让指定玩家客户端进行调试播放
- `/musichud playtest stop [玩家]`：停止调试播放

## 格式支持
- 常见本地文件与直链格式：`mp3`、`flac`、`wav`、`ogg/vorbis`、`ogg/opus`、`aac`、`m4a/mp4`、`aiff`、`au`
- `test` 指令会优先显示检测到的资源格式、解码后端、声道数、采样率与 OpenAL 格式
- `playtest` 指令可用于验证目标客户端是否能真实播放对应文件或 URL
- 如果某些文件仍然无法播放，建议优先用 `test` 查看识别结果，再用 `playtest` 复现客户端行为

## 使用
### 客户端
> 目前不支持在单人游戏中使用

在 mods 文件夹中放入 Architectury API, ModernUI 和 Forge Config API Port (仅Fabric需要) 这几个前置 mod 和 MusicHud 的 jar 文件即可
### 服务端
1. 部署 Netease Cloud Music API Enhanced (https://github.com/neteasecloudmusicapienhanced/api-enhanced)，或者使用本分支提供的 Paper 插件内置 API 管理能力
2. 如果不使用 NCM API Enhanced 的默认端口 ( 3000 ) 或在其他服务器上部署，需要修改配置文件的 `serverApiBaseUrl` 属性
3. 如果使用本仓库的 Paper 插件增强功能，建议一并给客户端更新本仓库对应版本的 Fabric / NeoForge 模组

配置文件位置 `/config/music_hud-server.toml`

配置文件默认内容
```toml
#Server API Base URL configuration
serverApiBaseUrl = "http://localhost:3000"
#Music Pusher's vote additional rate when voting for skip music configuration (0.0 ~ 1.0, total rate larger than or equals to 0.5 means to skip)
# Default: 0.5
# Range: 0.0 ~ 1.0
pusherVoteAdditionalRate = 0.5
#Use random Chinese IP provided by api server
useRandomCnIp = true

```

## 已知问题
- 未登录时，由于使用音源替换，部分音乐可能会出现音频瑕疵以及加载缓慢
- 不同来源的音频容器和编码差异较大，遇到问题时建议先使用 `test` / `playtest` 定位是识别、解码还是目标客户端环境问题
