# 刘杨春的个人博客

基于 Java 17、Spring Boot 3 和 Vue 3 的个人博客，记录泰和风物、人文与日常。文章、评论、用户和阅读量存入数据库；前台使用独立网址，后台支持完整的草稿和发布流程。

## Cloudflare 全托管

无需购买服务器，可使用 Pages + Pages Functions（Workers）+ D1 部署，并先使用免费的 `pages.dev` 平台域名。Cloudflare 版本兼容现有前端接口，保留原 Java 后端供其他部署方式使用；本地 H2 数据不会自动导入 D1。

线上站点：[刘杨春的泰和乡土手记](https://boke-16z.pages.dev)，后台地址为 `/admin`。生产使用 Cloudflare Pages 项目 `boke` 和已绑定的 D1 数据库；本地开发管理员与生产账号独立。账户授权、数据库迁移、管理员密钥设置和后续更新步骤见 [Cloudflare 中文部署指南](cloudflare/README.md)。

## 本地启动

在两个终端分别运行：

```powershell
cd D:\boke\backend
mvn spring-boot:run
```

```powershell
cd D:\boke\frontend
npm ci
npm run dev
```

访问 `http://localhost:5173`。前端通过同源 `/api` 代理访问 `http://127.0.0.1:8080`。

本地默认 `dev` profile，数据库文件保存在 `backend/data/`。关闭再启动仍会保留文章、评论、账户和阅读量。请始终从 `backend` 目录启动，或通过 `BLOG_DB_URL` 设置固定的绝对数据库路径。

仅开发环境创建示例内容和 `admin` / `admin123` 账户；这些凭据不在登录页面展示，不可用于公网部署。

## 页面和写作

- `/`：已发布文章、搜索与分类标签筛选。
- `/archives`：按时间浏览文章，筛选条件保留在网址中。
- `/about`：独立作者页。
- `/posts/:slug`：可直接访问、刷新、分享的文章地址，支持 Markdown 正文、目录、阅读进度、浏览量、上下篇、点赞和读者留言。
- `/admin`：管理员登录、文章搜索、保存草稿、发布、编辑、下架、删除、互动统计和留言管理。

读者无需注册即可点赞、取消点赞和提交留言。点赞使用浏览器保存的随机访客标识，同一标识对同一篇文章只计一次；清除浏览器数据或更换设备后会视为新的访客。新留言先进入待审核状态，只有审核通过的留言会公开显示。后台可按状态、文章和关键词筛选留言，审核、隐藏、删除留言并添加或修改作者回复，也可确认后清空某篇文章的点赞。

新增十篇围绕泰和农时、赣江、早市、快阁、槎滩陂、蜀口、武山乡味、地方记录、秋日摄影和步行路线的原创文章。开发环境在首次启动时一次性补入已有数据库，保留已有文章和后续编辑；删除新增文章后重启不会自动恢复。Cloudflare 通过 `0002_interactions.sql` 和 `0003_related_articles.sql` 迁移补齐互动功能及十篇文章，升级已有站点时需要应用迁移并重新发布。内容源与生产 Java 导入方法见 [相关文章说明](docs/related-articles.md)。

公开内容与后台列表分别加载，草稿不会因为登录或退出而出现在前台。接口失败时显示错误和重试入口，不回退到演示文章。文章编辑保留原始 Markdown；离开尚未保存的编辑时会确认。

## 生产配置

生产使用 `prod,mysql` profiles，MySQL 驱动、JDBC 仓储和 Flyway 数据库迁移已接入。配置以下环境变量：

- `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`：专用 MySQL 数据库与应用用户。
- `BLOG_JWT_SECRET`：至少 32 字节的随机密钥。
- `BLOG_ADMIN_USERNAME`、`BLOG_ADMIN_PASSWORD`、`BLOG_ADMIN_DISPLAY_NAME`：生产管理员，密码至少 12 个字符。
- `BLOG_JWT_EXPIRATION_MINUTES`：令牌有效期，生产默认 60 分钟。

生产配置不完整、使用开发密钥或混用 `prod,dev` 时启动失败。不要将真实密码提交到 Git。

完整配置、备份和迁移说明见 [后端文档](backend/README.md)。HTTPS、同源 API、登录/评论限流和文章地址刷新回退见 [部署文档](deploy/README.md) 与 [Nginx 示例](deploy/nginx.conf)。部署前替换域名、证书和静态目录。

本地 H2 只负责本地落盘运行；它不会自动把数据同步到 MySQL。开发示例内容也不会自动导入生产库。生产发布自己的内容需要在生产后台重新创建，或实施独立的数据导入。

## 验证

```powershell
cd D:\boke\backend
mvn test

cd D:\boke\frontend
npm test
npm run build
```

测试覆盖数据库重启、认证配置、草稿/发布可见性、点赞去重与取消、评论审核与作者回复、API 请求隔离、错误状态和 Markdown 数据处理。Cloudflare 接口测试在 `cloudflare` 目录运行 `npm test`。浏览器联调截图保存在 `output/playwright/`。
