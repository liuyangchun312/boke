# 刘杨春个人博客前端

这是 Vue 3 + Vite + Vue Router 的个人博客前台与编辑工作台，连接 Spring Boot REST API。

## 启动

```bash
npm install
npm run dev
```

默认开发地址：`http://localhost:5173`

## 页面与发布

- `/`：最新文章与搜索、分类、标签筛选。
- `/archives`：按时间浏览已发布文章。
- `/about`：独立作者页。
- `/posts/:slug`：可直接访问、刷新和分享的 Markdown 文章，包含阅读进度、浏览量、上下篇与读者留言。
- `/admin`：登录后保存草稿、编辑、发布、下架和删除文章。
- 未知地址和不存在的文章显示未找到页面，接口异常显示重试入口。

公开页面不使用模拟数据，也不复用包含草稿的后台列表。编辑器保留原始 Markdown；退出未保存编辑前会确认。开发账户仅由后端 dev profile 创建，不在登录页显示。

## 配置与验证

默认 API 地址为 `/api`。Vite 将请求代理至 `http://127.0.0.1:8080`，因此需要同时启动后端。确需跨域部署时才设置 `VITE_API_BASE_URL` 并配置后端允许的来源。生产构建前设置 `VITE_SITE_URL=https://你的域名`，用于 canonical、Open Graph 与文章结构化数据中的绝对网址。

```powershell
npm test
npm run build
```

生产部署 `dist/` 时，应将 `/api/` 转发到后端，并为所有前端路由配置 `index.html` 回退，否则文章直达或刷新会被静态服务器返回 404。参考项目根目录的 `deploy/nginx.conf`，它同时配置 HTTPS、登录限流和静态资源缓存。上线前将证书、域名和静态目录替换为实际配置。
