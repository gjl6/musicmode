# Music Mode

个人音乐管理系统，用于组织、增强和编辑本地音频文件的元数据（ID3 标签等）。

## 项目结构

```
music-mode/
├── music-common/    # 共享数据层（model, mapper, ConfigService）
├── music-core/      # 业务逻辑库（Controller, Service, Module, Pipeline）
├── music-auth/      # Spring Boot 主应用（启动入口, Security, JWT 认证）
├── music-web/       # Vue 3 前端（Vite 8, Pinia 3, Naive UI 2.44）
└── music/           # 音乐文件存储（不入版本控制）
```

## 功能特性

- **文件浏览** — 本地音乐文件目录树浏览与管理
- **元数据编辑** — ID3 标签（标题、艺术家、专辑、风格、歌词）编辑，支持撤销/重做
- **管道处理** — DAG 拓扑驱动的可插拔处理管道
  - 文件扫描与解析（MP3/FLAC/WAV/MP4）
  - 元数据回写
  - 编码修复（乱码检测与修复）
  - 繁简中文转换
  - 在线元数据增强（多平台聚合搜索）
  - 声学指纹（Chromaprint/FFmpeg）
  - 重复文件检测
  - 歌词分割
  - 文本替换
  - 文件格式转换
  - 文件整理
- **WebSocket 实时推送** — 管道执行进度实时展示

## 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- Maven（使用自带 Maven Wrapper）

### 启动后端

```bash
# 从项目根目录启动 music-auth（主应用）
mvn spring-boot:run -pl music-auth
```

后端启动在 `http://localhost:8089`，H2 数据库 TCP Server 在 `9092` 端口。

使用 MySQL：

```bash
mvn spring-boot:run -pl music-auth -Dspring-boot.run.profiles=mysql
```

### 启动前端

```bash
cd music-web
npm install        # 首次运行
npm run dev        # 开发服务器 → http://localhost:5173
```

### 运行测试

```bash
# 全部测试（从根目录）
mvn test -pl music-auth

# 单个测试类
mvn test -pl music-auth -Dtest=ClassName

# 单个测试方法
mvn test -pl music-auth -Dtest=ClassName#methodName
```

### 构建

```bash
# 后端打包（跳过测试）
mvn package -pl music-auth -DskipTests

# 前端构建
cd music-web && npm run build
```

## 关键端口

| 端口 | 用途 |
|------|------|
| 8089 | Spring Boot 后端（H2 Console: http://localhost:8089/h2-console） |
| 9092 | H2 TCP Server |
| 5173 | Vite 前端开发服务器 |

## 技术栈

**后端**：Spring Boot 4.0.6 + Java 21 + MyBatis 3.0.4 + H2/MySQL + jaudiotagger + FFmpeg + Caffeine

**前端**：Vue 3.5 + Vite 8 + Pinia 3 + Vue Router 4 + Naive UI 2.44 + vue-i18n 11
