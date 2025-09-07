# Repository Guidelines

This document helps contributors work consistently in the morecat repository.

## Project Structure & Module Organization
- Source code: place application code under `src/` (e.g., `src/core/`, `src/api/`).
- Tests: mirror source layout under `tests/` (e.g., `tests/api/` for `src/api/`).
- Scripts & tooling: keep helper scripts in `scripts/` and docs in `docs/`.
- Assets: store static files in `assets/` or `public/` depending on the stack.

Example paths: `src/modules/auth/`, `tests/modules/auth/`, `scripts/dev.sh`.

## Build, Test, and Development Commands
- Build: `make build` — compiles or bundles the app if a Makefile exists.
- Test: `make test` — runs the full test suite with coverage.
- Dev server: `make dev` — starts a local development server/watch mode.

If Make is not available, use your stack’s standard commands (e.g., `npm run build`, `pytest -q`, `cargo test`). Add equivalents to a `Makefile` or `package.json` for consistency.

## Coding Style & Naming Conventions
- Formatting: use an auto-formatter for the chosen language (e.g., Prettier, Black, gofmt, rustfmt). Prefer project-wide settings checked into version control.
- Linting: enable a linter (e.g., ESLint, Ruff, Clippy) and keep CI passing.
- Naming: directories `kebab-case`, files follow language norms (`snake_case.py`, `PascalCase.tsx`). Public APIs use clear, descriptive names; avoid abbreviations.

## Testing Guidelines
- Framework: use the ecosystem default (e.g., Jest/Vitest, PyTest, Go test, Rust test).
- Structure: tests live in `tests/` mirroring `src/`. Name files like `test_*.py`, `*_test.go`, or `*.spec.ts` as appropriate.
- Coverage: target ≥80% line coverage for changed code. Include edge cases and failure paths.

## Commit & Pull Request Guidelines
- Commits: concise subject (≤72 chars) + context in body. Prefer conventional prefixes when useful (e.g., feat, fix, docs, refactor).
- PRs: include purpose, linked issues, and how to verify. Add screenshots for UI-affecting changes. Keep PRs focused and small.

## Security & Configuration Tips
- Secrets: never commit secrets. Use `.env.local` and commit a redacted `.env.example`.
- Config: prefer environment variables; document required keys in README and examples.

## Agent-Specific Notes
- Scope: this AGENTS.md applies repo-wide. Keep changes minimal and focused. Use `apply_patch` to modify files and mirror structure between `src/` and `tests/`.
