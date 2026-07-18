# morecat UI

The UI is a pnpm workspace containing the shared OpenAPI client and the viewer/editor applications.

Run commands from `apps/ui` inside the repository's Nix development shell:

```sh
pnpm install --frozen-lockfile
pnpm gen:api
pnpm test
pnpm typecheck
```

`pnpm gen:api` regenerates `openapi.yaml` from the tapir endpoint descriptions in `apps/api`, then regenerates `packages/api-client/src/schema.ts`. Commit both generated files whenever the API contract changes.
