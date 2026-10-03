# Taihe Editorial Redesign Implementation Plan

**Goal:** Rebuild the public blog as an original photographic field journal and publish the verified revision to GitHub and Cloudflare.

**Architecture:** Keep Vue routes, the public API, Markdown rendering and reader interactions. Replace the rural public theme and page compositions. Resolve only the bundled legacy covers to locally hosted, credited documentary photographs; preserve custom covers.

**Tech Stack:** Vue 3, Vue Router, Vite, Lucide, CSS, Cloudflare Pages and D1.

## Design

Use a full-width Taihe river photograph with the journal name in the first viewport. Pair Chinese serif titles with restrained sans-serif navigation and numbered editorial metadata. Use neutral white, charcoal, vermilion and the photographs' natural colors. Keep sections unframed, distinguish a lead story from a three-column article index, and provide clear category and topic navigation.

Motion uses brief entry and intersection reveals plus image and arrow hover feedback. Respect reduced motion, retain visible focus, and provide keyboard-operable search and mobile navigation. At 320, 390, 768, 1440 and 1920 pixels, text must fit, images must load and no page may overflow horizontally.

## Execution

- [x] Inspect existing routes, content, local edits and deployment settings; create an isolated checkout from origin/main.
- [x] Add photo attribution data and focused tests for legacy cover replacement, repeated normalization and custom cover preservation.
- [x] Optimize real photographs into local WebP variants; replace homepage, article index and shared navigation/footer.
- [x] Unify article, archive and author pages with the new visual system; implement responsive states and accessible motion.
- [x] Run frontend and Cloudflare tests, production build and browser checks of navigation, search, filters, article reading, menu and failure states.
- [x] Inspect desktop and mobile screenshots; correct typography, spacing, contrast, motion and interaction issues until the audit has no obvious outstanding defect.
- [x] Commit only task files, push main without force, publish to the existing Cloudflare project, and verify the live deployment and API.
- [x] Copy the verified task files to D:/boke while preserving other local work, and record the final audit evidence.
