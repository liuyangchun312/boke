# Deployment

The frontend and API are intended to share one HTTPS origin. The supplied `nginx.conf` is a template for an existing Nginx installation; this task does not deploy or change any external service.

1. Build `frontend` with `npm ci` and `npm run build`. Keep `VITE_API_BASE_URL=/api`, set `VITE_SITE_URL=https://your-domain.example`, and copy the contents of `dist/` into the configured static root.
2. Build `backend` with `mvn clean package`. Configure the production database, administrator bootstrap and JWT environment variables described in `backend/README.md`. Start with `prod,mysql`, using a dedicated MySQL database and restricted application user.
3. Replace the certificate paths, `server_name`, and static root in `nginx.conf` with the site's actual settings. Load it in Nginx's `http` context, for example through `conf.d/`. If TLS is terminated by an existing ingress, preserve its HTTPS enforcement and adapt the internal listener to that deployment.
4. Keep the backend on the loopback interface or a private service network. Only the HTTPS entry point should be public. The API proxy preserves `/api/` and forwards the original protocol and client address.
5. Run `nginx -t` on the deployment host before reloading. Verify a direct article URL and refresh, an unknown URL, the archives, administrator login, and publish/unpublish visibility from an unauthenticated browser.

The article URL fallback to `index.html` is necessary for Vue Router history mode. The frontend displays its own missing-page state. This SPA fallback does not provide article-specific HTML to non-JavaScript link-preview crawlers; server rendering and social-card generation are separate enhancements.

Login and reader-comment requests are rate limited by client IP in the example Nginx configuration. Public comments still require routine moderation; review and remove unsuitable entries from the administrator comment panel, or disable the comment route if the site cannot be monitored. If another proxy is in front of Nginx, configure trusted `real_ip` sources for that specific infrastructure before relying on IP-based limits. Do not trust arbitrary client-supplied forwarding headers.

## Backups

- Production MySQL: schedule a consistent database backup with the hosting provider or `mysqldump --single-transaction`, store it outside the application host, and periodically test restoration. Avoid putting database passwords in command arguments or Git.
- Local H2: stop the backend before copying the database files from `backend/data/`. Keep the configured database path stable across launches. Never copy only part of a live database as a backup.
- Keep environment secrets and TLS private keys outside the repository. An environment sample is documentation, not a mechanism for loading Spring configuration.

## Before Updating

Back up the database before applying a new release. Flyway applies committed migrations on startup; do not edit an already-applied migration. Deploy the matching backend API and frontend bundle together, then repeat the login and publishing checks. Keep the previous application artifact and a verified backup for recovery.
