# Blog backend

Spring Boot 3 / Java 17 API with Spring JDBC, Flyway migrations, BCrypt
passwords, and stateless JWT authentication.

## Local development

The default profile is `dev`. It stores data in the H2 file database under
`backend/data` when Maven is run from this directory. The development profile
creates the sample articles and the local-only account `admin` / `admin123` if
the database is empty.

```powershell
cd D:\boke\backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Override local settings with:

- `BLOG_DB_URL`, `BLOG_DB_USERNAME`, `BLOG_DB_PASSWORD`, `BLOG_DB_DRIVER`
- `BLOG_JWT_SECRET`, `BLOG_JWT_EXPIRATION_MINUTES`
- `BLOG_CORS_ALLOWED_ORIGINS` (comma-separated origins)

The development account and content are only created by the `dev` profile.
Never combine `dev` and `prod`; startup rejects that combination.

## Production with MySQL

Create an empty MySQL database and a least-privilege application user. Flyway
creates and upgrades the tables. Set every value below through the service
manager or secret store, then activate both profiles:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod,mysql'
$env:MYSQL_URL = 'jdbc:mysql://db.example:3306/blog?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:MYSQL_USERNAME = 'blog_app'
$env:MYSQL_PASSWORD = '<database password>'
$env:BLOG_JWT_SECRET = '<random secret of at least 32 UTF-8 bytes>'
$env:BLOG_JWT_EXPIRATION_MINUTES = '60'
$env:BLOG_ADMIN_USERNAME = '<administrator username>'
$env:BLOG_ADMIN_PASSWORD = '<administrator password, at least 12 characters>'
$env:BLOG_ADMIN_DISPLAY_NAME = '<public display name>'
$env:BLOG_SERVER_ADDRESS = '127.0.0.1'
mvn spring-boot:run
```

Production startup rejects a missing or development JWT secret, TTL outside
1-1440 minutes, missing/default bootstrap credentials, blank database
credentials, an in-memory datasource, and `prod,dev`. The administrator is
stored with a BCrypt hash. On later starts, the bootstrap values update that
account, which also provides a credential-rotation path. If an earlier dev run
left the `admin` account in the same database and production uses a different
administrator username, startup disables that development account.

Production binds to `127.0.0.1` by default for a same-host reverse proxy. Set
`BLOG_SERVER_ADDRESS` explicitly when the proxy reaches the backend over a
private network interface.

MySQL support is supplied by `mysql-connector-j` and `flyway-mysql`. No local
MySQL server or existing database is modified by the test suite.

For an explicitly approved durable H2 file datasource (or another JDBC driver
added to the build), activate `prod`, set explicit
`BLOG_DB_URL`, `BLOG_DB_USERNAME`, `BLOG_DB_PASSWORD`, and `BLOG_DB_DRIVER`, and
set `BLOG_ALLOW_DURABLE_DATASOURCE=true`. This acknowledgement is required so
production cannot silently fall back to the local H2 default. The migration
SQL must also be compatible with that database.

## Backups

For the local H2 database, stop the backend first, then copy the entire
`backend/data` directory to dated backup storage. Restore only while the
backend is stopped. Copying a live H2 file is not a consistent backup.

For MySQL, use the database provider's snapshot facility or `mysqldump` with a
password prompt rather than placing a password on the command line:

```powershell
mysqldump --single-transaction --host db.example --user blog_app --password --result-file=blog-backup.sql blog
mysql --host db.example --user blog_app --password --execute="source blog-backup.sql" blog
```

Back up before deploying a new migration. Test restoration periodically and
retain the Flyway schema-history table with the application tables.

## API outline

- `POST /api/auth/login`, `GET /api/auth/me`
- `GET /api/posts`, `GET /api/posts/{idOrSlug}`
- `GET /api/posts/slug/{slug}` for an unambiguous slug lookup, including numeric slugs
- `GET /api/categories`, `GET /api/tags` (published posts only)
- `GET/POST /api/posts/{id}/comments` (published posts only)
- `GET/PUT /api/posts/{id}/likes` (published posts only)
- `GET /api/admin/comments`, `DELETE /api/admin/comments/{id}`
- `PATCH /api/admin/comments/{id}/status`, `PUT /api/admin/comments/{id}/reply`
- `DELETE /api/admin/posts/{id}/likes` resets the post's likes
- `GET /api/admin/posts`, `GET /api/admin/posts/{id}`
- `POST /api/admin/posts`, `PUT/DELETE /api/admin/posts/{id}`
- `PATCH /api/admin/posts/{id}/publish`, `/unpublish`

All responses retain `{ success, message, data, timestamp }`. Omitted post
status defaults to `DRAFT`; omitted slugs remain stable on edits. Post content
is stored exactly as submitted so Markdown whitespace is preserved.

Like requests require an `X-Visitor-Id` header containing 16-128 letters,
digits, underscores, or hyphens. The frontend persists a random visitor UUID.
`GET` returns `data: { likeCount, liked }`; `PUT` accepts `{ "liked": true }`
or `{ "liked": false }` and returns the same state. Repeated requests are
idempotent for that visitor and post. These identifiers are anonymous browser
identities, not authenticated user accounts.

New comments have status `PENDING` and become public after an administrator
sets `APPROVED`. `HIDDEN` and `PENDING` comments appear only in the admin list.
The status endpoint accepts `{ "status": "APPROVED" }`, `"HIDDEN"`, or
`"PENDING"`. The reply endpoint accepts `{ "content": "Reply text" }` with a
1000-character limit. Replies are trimmed; blank content clears both `reply`
and `repliedAt`. Public replies follow their comment's visibility.

Post responses include `likeCount` and `commentCount`; only approved comments
contribute to `commentCount`. Flyway migration V2 preserves existing comments
as approved and cascades interaction removal when their post is deleted.
