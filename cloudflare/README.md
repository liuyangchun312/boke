# Cloudflare 全托管部署

前端使用 Pages；`functions/api/[[path]].js` 是运行在 Workers 上的 Pages Functions；数据保存在 D1。不需要 Java、MySQL 服务器或付费域名，可以先使用平台分配的 `pages.dev` 地址。现有 Java 后端保留，但这套部署不会调用它。

## GitHub 自动部署（Cloudflare Pages 控制台）

如果已在 Pages 中连接本仓库，在项目的设置中使用以下构建配置：

当前生产项目为 `boke`，地址为 `https://boke-16z.pages.dev`，D1 数据库为 `liuyangchun-blog-20261003`。

| 设置 | 值 |
| --- | --- |
| 框架预设 | `None` |
| 根目录（Root directory） | `cloudflare` |
| 构建命令（Build command） | `npm run build` |
| 构建输出目录（Build output directory） | `dist` |
| 生产分支 | `main` |

在构建环境变量中设置 `VITE_API_BASE_URL=/api`、`VITE_SITE_URL=https://你的项目.pages.dev` 和 `NODE_VERSION=22`，使用 Pages V2 或更新的构建系统，并保留默认依赖安装。

仓库根目录没有 `package.json`，在那里执行 `npm run build` 会报 `ENOENT`。`cloudflare/package.json` 会安装相邻的 `frontend`，通过 Vite 的 `--outDir` 将产物写入 `cloudflare/dist`，Pages 同时发现 `cloudflare/functions` 中的 API。根目录应填 `cloudflare`，仅使用 `frontend` 会遗漏后端 Functions。输出目录使用根目录内的 `dist`，带 `..` 的路径会被 Pages 拒绝。

首次部署前，按下文第 1、2 步完成 Wrangler 授权、创建 D1 和远端数据库迁移，将真实数据库 ID 填入 `cloudflare/wrangler.toml` 后提交并推送。全零 ID 是占位符，不能用于部署；Wrangler 配置中的 D1 绑定是部署配置来源。

在现有 Pages 项目的生产环境设置以下运行时 Secrets，然后重新部署：

- `BLOG_ADMIN_USERNAME`：管理员登录名。
- `BLOG_ADMIN_DISPLAY_NAME`：管理员公开显示名，可选。
- `BLOG_ADMIN_PASSWORD_HASH`、`BLOG_JWT_SECRET`：在本机 `cloudflare` 目录运行 `npm run admin:hash` 生成，按下文第 3 步设置到现有项目。

数据库或必需的管理员配置缺失时，API 会返回 503，包括公开文章接口。Git 自动部署使用现有的 Git 关联项目，第 3 步的 `pages project create` 和第 4 步的手动发布命令适用于 Wrangler 直接上传方式；两种项目类型不能互相切换。

保存构建设置并推送真实 D1 配置后，重试部署，核对构建日志中的工作目录、Functions 打包和发布结果。参考 [Pages 构建配置](https://developers.cloudflare.com/pages/configuration/build-configuration/)、[Functions 目录](https://developers.cloudflare.com/pages/functions/get-started/) 和 [Wrangler 配置](https://developers.cloudflare.com/pages/functions/wrangler-configuration/)。

## Wrangler 直接上传部署

## 1. 注册并登录

打开 https://dash.cloudflare.com/ 注册账户，验证邮箱，建议开启双重验证。先使用免费方案，实际额度和计费以控制台为准，不承诺永久免费。国内网络访问平台子域名的情况需要实际测试。

在本机 PowerShell 执行：

```powershell
cd D:\boke\cloudflare
npm ci
npx wrangler login
```

浏览器中授权 Wrangler 即可。不要把账户密码、API Token、管理员密码发到聊天或提交 Git。

## 2. 创建 D1

```powershell
npx wrangler d1 create blog
```

将返回的 `database_id` 和数据库名称填入本目录 `wrangler.toml`。当前配置使用 `liuyangchun-blog-20261003`；如果创建了其他数据库，同时更新 `database_name` 和 `database_id`。保留 `binding = "DB"`。

```powershell
npm run db:remote
```

此命令在远端创建文章、评论及限流表；没有示例文章、没有默认管理员密码。首次为空库是正常现象。

## 3. 创建 Pages 项目并配置管理员

```powershell
npx wrangler pages project create liuyangchun-blog --production-branch main
npm run admin:hash
```

项目名若已被占用，换一个名字，并同步修改 `wrangler.toml` 的 `name`。密码生成脚本会隐藏输入，需要输入两次至少 12 个字符的管理员密码，然后输出密码哈希及随机 JWT 密钥。妥善保管密码，终端中的输出也应当保密。

依次执行以下命令，在交互提示中输入对应的值；`--project-name` 必须与实际项目名一致：

```powershell
npx wrangler pages secret put BLOG_ADMIN_USERNAME --project-name liuyangchun-blog
npx wrangler pages secret put BLOG_ADMIN_DISPLAY_NAME --project-name liuyangchun-blog
npx wrangler pages secret put BLOG_ADMIN_PASSWORD_HASH --project-name liuyangchun-blog
npx wrangler pages secret put BLOG_JWT_SECRET --project-name liuyangchun-blog
```

前两个值分别是你自选的登录名、公开显示名称。后两个填脚本输出中等号后面的值，不含变量名。

## 4. 构建并发布

```powershell
$env:VITE_API_BASE_URL = '/api'
$env:VITE_SITE_URL = 'https://liuyangchun-blog.pages.dev'
npm run build
npm run deploy -- --project-name liuyangchun-blog --branch main
```

`VITE_SITE_URL` 换成你的实际项目域名。必须在 `cloudflare` 目录执行发布，Wrangler 才会同时打包这里的 Functions。不要只在控制台拖拽 `dist`，否则后端不会一起上传。

发布后的实际地址以 Wrangler 输出为准。访问 `/admin`，用刚设置的用户名和原始密码登录，创建并发布自己的第一篇文章。

检查首页、文章直达链接和刷新、搜索分类、退出登录后的草稿不可见性、评论及后台删除评论。静态文件走 Pages，只有 `/api/*` 进入 Functions；Wrangler 自动生成 API 路由配置。SPA 页面依赖 Pages 默认回退，不要向构建目录添加顶层 `404.html`。

不要给不可信的预览部署绑定生产 D1 或生产密钥。当前教程只发布 `main` 生产分支；多人协作时为预览环境单独配置数据库和管理员密钥。

## 本地验证

```powershell
cd D:\boke\cloudflare
Copy-Item .dev.vars.example .dev.vars
npm run admin:hash
```

将生成值填入 `.dev.vars` 并设置用户名和显示名，再运行：

```powershell
npm run db:local
npm run build
npm run dev
```

访问 http://127.0.0.1:8788 。本地 D1 与线上 D1 分离；`.dev.vars` 和 `.wrangler` 已忽略，不可提交。

```powershell
npm test
npm --prefix ../frontend test
```

## 安全、额度和备份

- 密码使用 PBKDF2-SHA256（随机盐、100000 次迭代，兼容 Workers Web Crypto），JWT 使用 jose 验证 HS256、签发者、受众和过期时间，有效期一小时。此配置受 Workers Web Crypto 迭代限制影响；使用长且唯一的管理员密码。变更密码哈希或 JWT 密钥后重新部署，旧登录令牌失效。
- 每个 IP 每分钟最多 5 次登录、3 次评论尝试，限流状态存 D1，IP 经带密钥摘要后保存。固定时间窗限流是基础保护，不是完整反垃圾系统，仍需管理评论；高风险站点应加 Turnstile。
- 请求 JSON 限制 512 KiB；文章正文最多 200000 字符。所有 SQL 用户参数绑定，草稿不出现在公开文章、分类、标签或评论接口。
- 搜索可能扫描文章正文；阅读量和限流产生数据库写入。留意 Workers CPU/请求次数及 D1 读写/存储额度，免费方案上线后需验证真实请求表现，尤其密码登录。不够用时再决定付费或优化。
- 后台评论列表和单篇评论列表沿用原 API 返回完整列表，评论量增大后应增加分页。
- 原前端封面等外链图片仍是外链，本次不包含图片上传或 R2 存储。外部图片可用性需另行检查。
- 本地 H2 的文章、账户和评论不会自动迁移。需要保留已有内容时先备份，再单独实施导入；不要上传 `backend/data`。
- D1 备份示例：`npx wrangler d1 export DB --remote --output blog.sql.backup`。备份含草稿和评论，应存放在私密位置并验证恢复；也可结合平台可用的 Time Travel 功能。发布数据库迁移前先备份。

## 后续更新

代码更新后运行测试、构建，再使用相同项目名和生产分支部署。若新增数据库迁移，备份后先执行 `npm run db:remote`，再发布匹配代码。生产使用的已应用迁移文件不可随意修改。

以后购买域名，在 Pages 项目的 Custom domains 中添加并按控制台完成 DNS 配置，更新 `VITE_SITE_URL` 后重新构建发布。
