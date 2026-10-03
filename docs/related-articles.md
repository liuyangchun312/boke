# 泰和乡土手记：十篇新增内容

规范内容保存在 `backend/src/main/resources/seed/related-articles.json`。本批十篇为原创编辑稿，沿用现有五种文章分类和本地 SVG 主题封面；每篇包含四个小标题，正文约 786 至 861 个汉字。日期采用 2026 年 9 月的十个不同日期，便于列表稳定排序。

文章围绕现有泰和、赣江、快阁、槎滩陂、蜀口和武山主题，提供阅读、观察、饮食记录与慢行安排。它们不声称已经完成实地访问或人物采访。所涉地理和文学背景延续原有文章主题；没有增加未经核实的历史年份、开放时间、票价、具体采访或医疗功效。文中提示读者核对当前公开资料与现场条件。SVG 封面是主题插画，不作为现场照片使用。

| 标题 | Slug | 分类 |
| --- | --- | --- |
| 把一年写进田埂：泰和农时观察笔记 | `taihe-farming-season-notes` | 江河田园 |
| 沿赣江慢走：把江岸读成日常 | `ganjiang-riverside-walking-notes` | 江河田园 |
| 早市里的季节：澄江买菜小记 | `chengjiang-morning-market-seasons` | 小城日常 |
| 在快阁读一首诗：从远山到归舟 | `reading-deng-kuaige-in-taihe` | 人文古迹 |
| 顺着水渠看槎滩陂：一份田野记录的方法 | `chatanbei-irrigation-field-notes` | 水利遗产 |
| 蜀口村庄的边界：岛上散步与田园相处 | `shukou-village-respectful-walk` | 江河田园 |
| 一锅汤里的武山记忆：乌鸡与家常餐桌 | `wushan-black-bone-chicken-table-culture` | 乡味物产 |
| 乡土故事怎样记：给地方记忆留出处 | `taihe-local-heritage-recording-notes` | 人文古迹 |
| 秋天的泰和怎么拍：光线、田野与分寸 | `taihe-autumn-photography-notes` | 江河田园 |
| 给泰和留半天：一份可以折返的慢行安排 | `taihe-half-day-slow-walk` | 小城日常 |

## Java 开发环境

`DataSeeder` 仅在 `dev` 配置启用。原有五篇仍只在数据库为空时创建；新增批次会在已有数据库上单独运行，先检查固定 slug，仅插入缺失文章。整批导入与 `blog_seed_runs` 中的 `related-articles-2026-10-v1` 标记在同一个事务提交。失败会回滚本批和标记，可以在修复后重新启动。

成功导入后，重启不会覆盖修改，也不会恢复删除或重命名的新增文章。保留数据库中的种子标记；不要为了重新导入而清空它。Java 的新文章保留 JSON 中的创建、修改和发布日期。

## Cloudflare D1

`cloudflare/migrations/0003_related_articles.sql` 由同一份 JSON 生成，采用 `INSERT OR IGNORE` 保留同 slug 的已有文章。通过 D1 迁移工具一次应用，后续部署不会重新应用已登记的迁移。若手工重新执行 SQL，被删除的条目可能重新插入，因此日常更新应使用后台编辑。

在仓库根目录生成或核对：

```powershell
node cloudflare/scripts/generate-related-articles.mjs
node cloudflare/scripts/generate-related-articles.mjs --check
```

## Java 生产环境的显式导入

生产环境不会启动开发种子。`backend/scripts/import-related-articles.mjs` 可通过已认证的管理员 API 导入，默认只预览，不写入文章。需要 Node.js 18 或以上；为 `BLOG_IMPORT_TOKEN` 设置当前管理员 JWT，令牌从环境读取，不会写进回执或日志。使用受信任的 API 地址，远程地址要求 HTTPS。

```powershell
node backend/scripts/import-related-articles.mjs --base-url https://your-java-api.example --receipt backend/data/related-articles-import.json
node backend/scripts/import-related-articles.mjs --base-url https://your-java-api.example --receipt backend/data/related-articles-import.json --apply
```

脚本分页读取管理员文章列表，只创建缺失的 slug，不更新已有条目。`--apply` 会逐条保存回执；重试使用同一回执，可以保留之后的删除或重命名。不同 API 应使用不同回执路径。回执与数据库备份一起保留。导入期间暂停对这十个 slug 的编辑，避免并发占用；脚本检测 API 自动产生的后缀 slug 时会删除刚创建的重复条目并停止。

管理员创建 API 不接受自定义时间，所以此生产导入路径采用服务端当前时间，而正文、标题、分类、标签和封面与 JSON 相同。D1 迁移与 Java 开发种子保留规范文件中的历史日期。脚本不会自行发布部署，也不会修改数据库配置或管理员账号。

## 验证

`RelatedArticlesSeedTests` 检查已有数据库补入、同 slug 编辑保留、重启不恢复删除或重命名、十篇正文长度、小标题与日期。Cloudflare 的真实 D1 测试检查迁移和规范内容一致。内容修订后应重新生成尚未应用的迁移；已上线批次的修订请通过后台进行，避免修改已经应用的迁移文件。
