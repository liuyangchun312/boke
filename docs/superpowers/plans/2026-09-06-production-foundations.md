# Production Foundations Implementation Plan

**Goal:** Make the existing personal blog durable, navigable and usable for publishing.

**Architecture:** Preserve Vue 3 and Spring Boot REST contracts. Add Vue Router history routes, isolate public and administrator data, and replace memory storage with JDBC persistence. Use a file H2 database locally and a real MySQL profile for production, with versioned migrations and environment-provided credentials.

**Scope:** The user's approved five items: persistence, production login configuration, article URLs, real navigation, and publishing workflow.

## Constraints

- Preserve existing content and visual identity; do not publish or deploy externally.
- Existing repository has no commits and all source files are untracked, so work in place without creating a baseline commit.
- Dev-only sample account/content must never be provisioned in production.
- A failed request must not display mock content or claim success.
- Public routes never reuse administrator data containing drafts.
- Production history fallback and same-origin API forwarding must be documented and configured.

## Tasks

- [ ] Backend: Add JDBC repositories and Flyway migrations for posts, comments and users; retain IDs/slugs and store password hashes. Test fresh schema, reload persistence, draft isolation, publishing, comment lifecycle and unique slugs.
- [ ] Authentication: Separate dev/prod configuration, validate production secrets and bootstrap account, derive authorities from persisted users. Test rejected defaults and valid login.
- [x] Frontend foundation: Add Vue Router routes for home, archives, about, posts, admin, and 404; add request errors and API helpers; unit test content round trips and request behavior.
- [ ] Frontend views: Implement independently addressable reading and navigation, real sharing, request loading/error/empty states, and separate administrator list with draft/published filters and publish/unpublish actions. Preserve raw Markdown on edits and confirm discarded changes.
- [ ] Integration: Verify article refresh/back navigation, login, create draft, publish, edit, unpublish, logout, restart persistence, missing article, desktop/mobile layouts, and backend unavailable behavior.
- [ ] Delivery: Update configuration/deployment/backup documentation, run builds and tests, obtain an independent code review, and keep local preview available.

## Interfaces

- Existing `{ success, message, data, timestamp }` response envelope stays unchanged.
- Public list/detail: `GET /api/posts`, `GET /api/posts/{idOrSlug}`, and unambiguous `GET /api/posts/slug/{slug}` used by frontend article URLs.
- Authentication: `POST /api/auth/login`, `GET /api/auth/me`.
- Admin: `GET/POST /api/admin/posts`, `PUT/DELETE /api/admin/posts/{id}`, `PATCH /api/admin/posts/{id}/publish` and `/unpublish`.
- Posts use `DRAFT` and `PUBLISHED`; omitted status defaults to `DRAFT`.
- Frontend API uses `/api` by default; Vite and production reverse proxy forward it to the backend.

## Verification Record

- API: six original contract tests passed, then two additional regressions reproduced stale-401 session deletion and missing cancellation propagation. Both are fixed; eight API tests now pass.
- Article utilities: six tests cover raw Markdown, filters, archives, shared parser heading IDs, editor payload and validation. Combined frontend suite: 14 passing.
- Targeted API/router/deployment review addressed late-session responses, cancellation, query scrolling, initial homepage scroll, and login path variant limiting.
- Ruling: work in the existing directory because the repository has no HEAD and every source file is untracked. No baseline or unrelated files are committed.
- Ruling: local persistence uses H2 files because no MySQL password was provided. Production uses the real MySQL adapter and explicit environment configuration; the existing MySQL server and its databases are untouched.
