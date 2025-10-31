# Repository Guidelines

## Project Structure & Module Organization
The repository currently hosts a scaffolded mono-repo under `apps/`. Use `apps/api` for the backend service. Keep source in `apps/api/src`, configuration in `apps/api/config`, and integration tests in `apps/api/tests`. `apps/ui/cui` houses the terminal client; mirror the same `src`/`tests` layout and keep distributable assets under `apps/ui/cui/assets`. The web experience splits into `apps/ui/web/editor` and `apps/ui/web/viewer`; treat each as a standalone package with dedicated `src`, `public`, and `tests` folders. Remove `.gitkeep` files as soon as real content lands and add README stubs in each app explaining domain responsibilities.

## Build, Test, and Development Commands
Until automation is wired up, each app should expose a consistent set of npm scripts: `npm install` (bootstrap dependencies), `npm run dev` (start the local server or watcher), `npm run build` (produce deployable artifacts), and `npm test` (run unit suites). Add any app-specific flags inside the script definitions rather than at call sites. When you introduce repo-level tooling, wrap these scripts with a root `Makefile` target such as `make dev-web` or `make test-api` so agents have a single entry point.

## Coding Style & Naming Conventions
Default to TypeScript for both API and UI code, using 2-space indentation, trailing commas, and single quotes. Enforce formatting with Prettier and linting with ESLint in `--max-warnings=0` mode (`npm run lint`). Prefer `camelCase` for functions, `PascalCase` for React components or classes, and `kebab-case` for file/directory names. Keep environment variables scoped per app in `.env.local` files that are git-ignored.

## Testing Guidelines
Aim for fast unit coverage before merging. Co-locate unit specs in `tests` using the `*.spec.ts` suffix, and use `npm test -- --watch` while iterating. Add integration suites under `apps/api/tests` or `apps/ui/web/<app>/tests/integration`. Expand coverage reports to meet an 80% line target, and document any intentional gaps in the PR description.

## Commit & Pull Request Guidelines
Follow Conventional Commits (`type(scope): short summary`) to keep history searchable. Every pull request should include: a succinct summary, links to tracking issues, setup notes for reviewers, and screenshots or GIFs for UI-facing work. Ensure CI (or local `npm test`/`npm run lint`) has run before requesting review, and update AGENTS.md whenever workflows change.
