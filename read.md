# 刘杨春的泰和乡土手记

记录江西省吉安市泰和县的风物、人文与日常。前台采用真实摄影与中文刊物排版，支持文章搜索、分类与主题筛选、年月档案、Markdown 阅读、点赞和读者留言；后台支持管理员登录、草稿、发布和留言审核。

当前线上版本由 Cloudflare Pages、Pages Functions（Workers）与 D1 托管，保留 Java / Spring Boot 后端供本地开发或自建服务器使用。

> 仓库同时保留标准入口文档 [`README.md`](README.md)。本文件按仓库维护要求提供项目概览。

## 在线访问

**网站首页：[https://boke-16z.pages.dev](https://boke-16z.pages.dev)**

| 页面 | 当前网址 |
| --- | --- |
| 阅读手记 | [https://boke-16z.pages.dev/](https://boke-16z.pages.dev/) |
| 年月档案 | [https://boke-16z.pages.dev/archives](https://boke-16z.pages.dev/archives) |
| 关于作者 | [https://boke-16z.pages.dev/about](https://boke-16z.pages.dev/about) |
| 文章示例 | [给泰和留半天：一份可以折返的慢行安排](https://boke-16z.pages.dev/posts/taihe-half-day-slow-walk) |
| 管理后台 | [https://boke-16z.pages.dev/admin](https://boke-16z.pages.dev/admin) |

GitHub 仓库：[https://github.com/liuyangchun312/boke](https://github.com/liuyangchun312/boke)。

后台使用单独配置的生产管理员账户。线上文章、点赞和留言存入 D1，本地 H2 数据不会自动同步到线上。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vue Router、Vite、Markdown-It、DOMPurify、Lucide、自托管中文字体与 WebP 实拍图片 |
| 线上后端 | Cloudflare Pages Functions（Workers）、JWT |
| Java 后端 | Java 17、Spring Boot 3、Spring Security、Spring JDBC、JWT |
| 数据库 | 线上使用 Cloudflare D1；Java 本地开发使用 H2，也支持 MySQL + Flyway |
| 部署 | 当前为 Cloudflare Pages + Functions + D1，同源 `/api`；也可使用 Nginx 自建部署 |

## 主要功能

- 已发布文章展示、关键词搜索、分类和标签筛选
- 文章归档与可分享的文章固定链接
- Markdown 正文渲染、目录、阅读进度、浏览量与上下篇
- 无需注册的点赞、取消点赞和读者留言
- 留言审核、隐藏、删除及作者回复，只有审核通过的留言公开展示
- 管理员认证、草稿保存、发布、下架、编辑和删除
- 真实摄影封面、影像出处、响应式阅读、搜索弹窗与减少动态效果支持
- 开发与生产配置隔离，生产配置完整性校验
- Cloudflare D1 / Java Flyway 数据库迁移与数据持久化

2026-10-04 前端改版的排版、留白、视觉层级、色彩、动效、微交互和响应式自检见 [`docs/frontend-design-audit-2026-10-04.md`](docs/frontend-design-audit-2026-10-04.md)。图片来源与各自许可见 [`frontend/public/photos/CREDITS.md`](frontend/public/photos/CREDITS.md)。

## 项目结构

```text
boke/
├─ backend/           # Spring Boot API、数据库迁移和后端测试
├─ frontend/          # Vue 3 前端应用和前端测试
├─ cloudflare/        # Pages Functions API、D1 迁移与部署配置
├─ deploy/            # 自建服务器的 Nginx 示例与部署说明
├─ docs/              # 补充项目文档
├─ .gitignore         # Git 忽略规则
├─ README.md          # 完整使用说明
└─ read.md            # 项目概览
```

## 环境要求

前端开发与 Cloudflare 构建建议使用 Node.js 22 和 npm。Cloudflare 部署需要已授权的账户，当前线上方案不依赖 Java、MySQL 或 Nginx 服务器。

运行 Java 后端时另需 JDK 17、Maven 3.9 或兼容版本。选择自建 MySQL 生产方案时，再准备 MySQL 8.x 与 Nginx。

## 本地启动

### 1. 启动后端

```powershell
cd D:\boke\backend
mvn spring-boot:run
```

后端默认运行在 [http://localhost:8080](http://localhost:8080)。开发环境使用 H2 文件数据库，数据保存在 `backend/data/`，此目录不会提交到 Git。

### 2. 启动前端

另开一个终端：

```powershell
cd D:\boke\frontend
npm ci
npm run dev
```

浏览器访问 [http://127.0.0.1:5173](http://127.0.0.1:5173)。开发服务器会将同源 `/api` 请求代理到本地 Java 后端。

预览线上内容时，也可以在前端终端将 API 代理指向现有站点：

```powershell
$env:BLOG_DEV_API_TARGET = 'https://boke-16z.pages.dev'
npm run dev
```

开发环境使用本地账号 `admin` / `admin123`，与生产账户独立。该账号仅用于本地开发。示例文章的补入规则及现有数据库的升级方式见 [`docs/related-articles.md`](docs/related-articles.md)。

## 测试与构建

Java 后端测试：

```powershell
cd D:\boke\backend
mvn test
```

前端测试与生产构建：

```powershell
cd D:\boke\frontend
npm test
npm run build
```

Cloudflare API 测试：

```powershell
cd D:\boke\cloudflare
npm ci
npm test
```

构建目录、依赖、运行日志、本地数据库、环境变量文件及浏览器测试产物均已加入 `.gitignore`。

## 生产配置

### 当前 Cloudflare 方案

生产 Pages 项目为 `boke`，站点地址为 [https://boke-16z.pages.dev](https://boke-16z.pages.dev)，GitHub 生产分支为 `main`。

| Pages 构建设置 | 值 |
| --- | --- |
| 根目录 | `cloudflare` |
| 构建命令 | `npm run build` |
| 构建输出目录 | `dist` |

生产运行时使用 D1 的 `DB` 绑定，以及 `BLOG_ADMIN_USERNAME`、`BLOG_ADMIN_PASSWORD_HASH`、`BLOG_JWT_SECRET` 等 Secrets。数据库迁移、管理员配置、GitHub 自动部署与后续更新见 [`cloudflare/README.md`](cloudflare/README.md)。

### Java 自建服务器方案

选择此方案时启用 `prod,mysql` profiles，并通过环境变量或密钥服务配置：

- `MYSQL_URL`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `BLOG_JWT_SECRET`
- `BLOG_ADMIN_USERNAME`
- `BLOG_ADMIN_PASSWORD`
- `BLOG_ADMIN_DISPLAY_NAME`
- `BLOG_JWT_EXPIRATION_MINUTES`

不要将真实密码、JWT 密钥、`.env` 文件、数据库文件或 TLS 私钥提交到仓库。

Java 配置、数据库备份与迁移说明见 [`backend/README.md`](backend/README.md)，Nginx 和自建服务器上线步骤见 [`deploy/README.md`](deploy/README.md)。

## 页面入口

上方“在线访问”提供可直接打开的完整网址。路由分别为 `/`、`/archives`、`/about`、`/posts/:slug` 和 `/admin`；文章使用实际 slug，例如 `taihe-half-day-slow-walk`，详情页支持直接访问与刷新。

## 开源说明

这是个人博客项目。若计划公开复用，建议在仓库中补充合适的代码许可证后再发布或分发。实拍图片保留各自的摄影者、来源和许可，复用时需遵循 [`图片许可说明`](frontend/public/photos/CREDITS.md)。
