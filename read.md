# 刘杨春的个人博客

一个前后端分离的个人博客系统，用于记录泰和风物、人文与日常。项目包含公开博客、文章归档、Markdown 阅读、读者评论，以及管理员登录、草稿、发布和评论管理等功能。

> 仓库同时保留标准入口文档 [`README.md`](README.md)。本文件按仓库维护要求提供项目概览。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vue Router、Vite、Markdown-It、DOMPurify、Lucide |
| 后端 | Java 17、Spring Boot 3、Spring Security、Spring JDBC、JWT |
| 数据库 | 本地开发使用 H2，生产环境支持 MySQL + Flyway |
| 部署 | Nginx、前后端同源 HTTPS、`/api` 反向代理 |

## 主要功能

- 已发布文章展示、关键词搜索、分类和标签筛选
- 文章归档与可分享的文章固定链接
- Markdown 正文渲染、目录、阅读进度、浏览量与上下篇
- 读者评论与后台评论管理
- 管理员认证、草稿保存、发布、下架、编辑和删除
- 开发与生产配置隔离，生产配置完整性校验
- Flyway 数据库迁移及本地数据持久化

## 项目结构

```text
boke/
├─ backend/           # Spring Boot API、数据库迁移和后端测试
├─ frontend/          # Vue 3 前端应用和前端测试
├─ deploy/            # Nginx 示例与部署说明
├─ docs/              # 补充项目文档
├─ .gitignore         # Git 忽略规则
├─ README.md          # 完整使用说明
└─ read.md            # 项目概览
```

## 环境要求

- JDK 17
- Maven 3.9 或兼容版本
- Node.js 18+（建议使用当前 LTS 版本）
- npm
- 生产部署时需要 MySQL 8.x 与 Nginx

## 本地启动

### 1. 启动后端

```powershell
cd D:\boke\backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。开发环境使用 H2 文件数据库，数据保存在 `backend/data/`，此目录不会提交到 Git。

### 2. 启动前端

另开一个终端：

```powershell
cd D:\boke\frontend
npm ci
npm run dev
```

浏览器访问 `http://localhost:5173`。开发服务器会将同源 `/api` 请求代理到后端。

开发环境仅在空数据库中创建示例内容及本地账号 `admin` / `admin123`。该账号只适合本地开发，禁止用于公网部署。

## 测试与构建

后端测试：

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

构建目录、依赖、运行日志、本地数据库、环境变量文件及浏览器测试产物均已加入 `.gitignore`。

## 生产配置

生产环境建议启用 `prod,mysql` profiles，并通过部署平台的环境变量或密钥服务配置：

- `MYSQL_URL`
- `MYSQL_USERNAME`
- `MYSQL_PASSWORD`
- `BLOG_JWT_SECRET`
- `BLOG_ADMIN_USERNAME`
- `BLOG_ADMIN_PASSWORD`
- `BLOG_ADMIN_DISPLAY_NAME`
- `BLOG_JWT_EXPIRATION_MINUTES`

不要将真实密码、JWT 密钥、`.env` 文件、数据库文件或 TLS 私钥提交到仓库。

详细配置、数据库备份与迁移说明见 [`backend/README.md`](backend/README.md)，Nginx 和上线步骤见 [`deploy/README.md`](deploy/README.md)。

## 页面入口

- `/`：博客首页
- `/archives`：文章归档
- `/about`：作者介绍
- `/posts/:slug`：文章详情
- `/admin`：管理后台

## 开源说明

这是个人博客项目。若计划公开复用，建议在仓库中补充合适的开源许可证后再发布或分发。
