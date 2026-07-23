# morecat viewer

The public Next.js SSR application. It reads published articles only through the generated `@morecat/api-client`; it does not connect to Postgres directly.

Set the server-only API origin in `.env.local`:

```sh
MORECAT_API_BASE_URL=http://localhost:8080
```

Run commands from `apps/ui` inside `nix develop`:

```sh
pnpm --filter @morecat/viewer dev
pnpm --filter @morecat/viewer test
pnpm --filter @morecat/viewer typecheck
pnpm --filter @morecat/viewer lint
pnpm --filter @morecat/viewer build
```

`/posts/{slug}` is forced dynamic and its API request uses `cache: 'no-store'`. Slice 1 intentionally performs a live read on every request; ISR and RMU-triggered revalidation remain a later change.
