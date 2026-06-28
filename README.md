# 🎵 Music Mode

> Java 原生音乐元数据管理系统 — 浏览器远程管理本地曲库，Docker 一行命令部署

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-green)
![Docker](https://img.shields.io/badge/Docker-amd64|arm64-blue)
![License](https://img.shields.io/badge/License-GPL_v3-orange)
![Image Size](https://img.shields.io/badge/image-~180MB-success)

---

## 这是什么

一个运行在你 NAS / 服务器上的 Web 音乐管理工具。打开浏览器就能编辑本地音频文件的元数据——歌名、艺人、专辑、封面、歌词等等。

和本地桌面工具（MP3Tag、Picard）不同，Music Mode 跑在你的远程机器上，操作的是远程机器上的文件。你不需要把 NAS 上的几百 GB 曲库挂载到本地再编辑。

同类工具大多用 Python 写的，万级曲库场景下 I/O 会卡。Music Mode 用 Java 21 的虚拟线程做并发，加上 DAG 管道把任务拆成可并行的步骤，整体吞吐量不在一个量级。

镜像经过四阶段构建（Maven 编译 → jlink 裁 JRE → Node 编译前端 → Alpine 编译纯音频 FFmpeg），最终运行时只有 **~180MB**。

---

## 和其他工具不太一样的地方

**歌曲、艺人、专辑各管各的。** 大部分工具只管歌曲 ID3 标签。Music Mode 把艺人信息和专辑信息当作一等公民：艺人有自己的增强/规范化/合并管道，专辑也一样。比如你把"周杰伦"和"Jay Chou"合并成一个艺人，关联的所有歌曲会自动更新。

**数据源你自己可以加。** 系统内置了多个平台的聚合搜索，但如果你有自己特有的数据来源，可以通过 HTTP 桥接或者上传 Java 源码让系统动态编译加载——不需要改代码、不需要重新构建镜像。

**不是单用户工具。** 有完整的三级权限体系（播放者 / 编辑者 / 管理员），家里多人共用一台 NAS 的时候各人有各人的权限。

**Watch 自动任务。** 设定好规则，曲库里新增的文件自动触发对应的处理管道——你只需要往目录里丢文件，剩下的它自己搞定。

---

## 快速开始

### Docker（推荐）

```bash
docker pull gjl168168/music-mode:latest

docker run -d \
  -p 8089:8089 \
  -v /你的/音乐/目录:/music \
  -v /你的/数据/目录:/data \
  -e JWT_SECRET=换一个安全密钥 \
  --restart=always \
  gjl168168/music-mode:latest
```

打开 `http://localhost:8089`，账号 `admin`，密码 `admin123`。**首次登录记得改密码。**

> 镜像支持 amd64 和 arm64，群晖 / 威联通 / 树莓派都能跑。

### Docker Compose

```yaml
services:
  music-mode:
    image: gjl168168/music-mode:latest
    container_name: music-mode
    ports:
      - "8089:8089"
    volumes:
      - /你的/音乐/目录:/music
      - ./data:/data
    environment:
      - JWT_SECRET=换一个安全密钥
      - SPRING_PROFILES_ACTIVE=docker
    restart: unless-stopped
```

### 挂 MySQL（曲库大了推荐）

内置的 H2 数据库开箱即用，零配置。曲库规模上去之后可以切到 MySQL：

```yaml
services:
  music-mode:
    image: gjl168168/music-mode:latest
    ports:
      - "8089:8089"
    volumes:
      - /你的/音乐/目录:/music
      - ./data:/data
    environment:
      - SPRING_PROFILES_ACTIVE=docker,docker-mysql
      - JWT_SECRET=换一个安全密钥
      - MYSQL_HOST=mysql
      - MYSQL_DATABASE=musicdb
      - MYSQL_USER=music
      - MYSQL_PASSWORD=music123
    restart: unless-stopped

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: musicdb
      MYSQL_USER: music
      MYSQL_PASSWORD: music123
    volumes:
      - mysql_data:/var/lib/mysql
    restart: unless-stopped

volumes:
  data:
  mysql_data:
```

### 从源码构建

```bash
cp .env.example .env     # 填好 MUSIC_DIR 和 JWT_SECRET
docker compose up -d      # H2 模式
docker compose --profile mysql up -d   # MySQL 模式
```

---

## 能做什么

### 🎧 歌曲

支持 FLAC / APE / WAV / AIFF / WV / TTA / MP3 / M4A / OGG / MPC / OPUS / WMA / DSF / MP4 全格式。

- **编辑标签** — 标题、艺人、专辑、风格、歌词、封面，单条或批量，操作可撤销
- **自动刮削** — 多平台聚合搜索，补全缺失的元数据。也支持声学指纹识别，文件名全乱也能匹配
- **乱码修复** — 自动检测编码问题并修正，繁简体一键互转
- **文本批处理** — 正则替换清理脏标签，从文件名解析出歌名艺人
- **格式转换** — FFmpeg 无损转码
- **CUE 分轨** — 整轨文件自动切割，每个分轨写入独立标签
- **文件整理** — 按 艺术家/专辑 自动归类目录，自定义多级结构
- **去重** — 检测重复文件

### 🎤 艺人

- 多平台聚合补全艺人资料、简介、头像
- 名称标准化（统一别名、简繁、大小写变体）
- 智能合并重复艺人

### 💿 专辑

- 多平台聚合补全专辑信息、封面、曲目列表
- 专辑名称规范化
- 智能合并重复专辑

### ⚡ 自动化

- **Watch 规则** — 监控目录变化或定时触发，文件到了自动跑管道
- **WebSocket** — 处理进度实时推送到浏览器
- **23 个管道模板** — 覆盖浏览、解析、写入、修复、增强、指纹、去重、整理等全部场景

### 🔊 播放 & 搜索

- 浏览器内 HTTP Range 流媒体播放，支持 FFmpeg 实时转码
- Lucene 中文全文搜索
- Subsonic / OpenSubsonic API，Navidrome / Jellyfin / Symfonium 可以直接连上来
- M3U 播放列表、播放队列、多端状态同步
- 播放统计 + 可视化图表

---

## 项目结构

```
music-mode/
├── music-common/        # 共享层：实体、Mapper、解析器、扫描器、搜索引擎、Watch 框架
├── music-editor/        # 业务核心：Pipeline 引擎 + 27 个处理模块
├── music-auth/          # 启动入口：Spring Boot + Security + JWT + RBAC
├── music-playback/      # 播放库：流媒体、转码、Subsonic、搜索 API
├── music-web/           # 前端：Vue 3 + Vite + Naive UI
└── music/               # 本地音乐存储（不入版本控制）
```

```
music-auth ──→ music-editor ──→ music-common
    │               │
    └──→ music-playback ──→ music-common
```

---

## 技术栈

| 层 | 选型 |
|----|------|
| 语言 & 运行时 | Java 21, Spring Boot 4.0.6 |
| 数据库 | H2（默认）/ MySQL 8.0 + Redis（缓存） |
| ORM | MyBatis 3.0.4 |
| 音频 | jaudiotagger, JavaCV/FFmpeg, JTransforms, Chromaprint |
| 搜索 | Apache Lucene 9.12（smartcn 中文分词） |
| 前端 | Vue 3.5, Vite 8, Pinia 3, Naive UI 2.44, vue-i18n 11 |
| 实时通信 | STOMP over SockJS |
| 其他 | opencc4j, Caffeine, Lettuce, Jackson XML |

---

## 本地开发

需要 Java 21+、Node.js 18+、Maven 3.9+。

```bash
# 后端
mvn spring-boot:run -pl music-auth

# 前端（另开终端）
cd music-web && npm install && npm run dev   # → localhost:5173
```

| 端口 | 用途 |
|------|------|
| 8089 | 后端 API |
| 5173 | Vite 开发服务器 |
| 9092 | H2 TCP Server |

```bash
# 测试
mvn test -pl music-auth
mvn test -pl music-auth -Dtest=具体类名
mvn test -pl music-auth -Dtest=具体类名#具体方法

# 打包
mvn package -pl music-auth -DskipTests
cd music-web && npm run build
```

---

## License & 免责声明

本项目以 **GPL V3.0** 协议开源。以下为补充条款，有冲突时以补充条款为准。

**名词定义** — "本项目"指 Music Mode；"使用者"指运行本软件的个人或组织；"版权数据"指图像、音频、歌词、名称等他人拥有合法版权的内容。

1. 本项目**仅编辑使用者本地已有音频文件的元数据**，不提供、不存储、不传播任何音乐文件本体。使用者应确保本地文件来源合法。
2. 元数据来自各音乐平台的公开接口，本项目不对其准确性、完整性负责。
3. 使用过程中产生的版权数据，使用者须在 **24 小时内清除**。
4. **禁止商业用途**——包括但不限于售卖、捆绑销售、付费服务。本项目仅供个人学习研究。
5. 因使用或无法使用本项目造成的任何损失，由使用者自行承担。
6. **禁止在违反当地法律的情况下使用本项目。**
7. 使用即表示同意以上全部条款。如不同意，请删除本项目及其所有副本。

---

## 反馈

有问题或想法 → [GitHub Issues](https://github.com/gjl6/musicmode/issues)
