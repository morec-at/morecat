# Repository Guidelines

設計の全体像は `docs/design.md`、構成図は `docs/architecture.html` を参照。本ファイルは貢献者 / エージェント向けの作業ガイド。

## Project Structure & Module Organization
モノレポ。コードは `apps/` 配下に集約（リポジトリ直下は `docs/` と meta のみ）。Scala バックエンドは **単一 sbt ビルド**（ビルドルート=`apps/`、`apps/build.sbt` + `apps/project/`）、UI は **pnpm workspace**。dev 環境はルートの `flake.nix`（`nix develop`）。

- `apps/api` — JVM デプロイ単位（Cloud Run）。sbt ビルドルート。tapir HTTP サーバ。サブプロジェクト: `domain`（純粋: Article イベント ADT・値オブジェクト(Iron)。IO や wire フォーマットを持たない。投影 fold は RMU、コマンド側集約ロードは今後）/ `application`（ユースケース）/ `infrastructure`（Firestore JVM SDK・Postgres・GCS・tapir・**JSON codec**）/ `bootstrap`。
- `apps/rmu` — **Rust** デプロイ単位（Cloud Run, Cargo）。Eventarc 起動の Read Model Updater。axum + serde + sqlx + Firestore クレート。ドメインは API と**コード共有せず**、イベント wire スキーマを契約フィクスチャで整合。
- `apps/ui/web/viewer` — Next.js SSR（Firebase App Hosting）。
- `apps/ui/web/editor` — Vite + React SPA（Firebase Hosting）。
- `apps/ui/packages/api-client` — OpenAPI から生成した型 / クライアント（viewer・editor で共有）。
- `apps/ui/cui` — v1 ではドロップ（プレースホルダ維持）。
- `docs/` — 設計・図。

`.gitkeep` は実体が入った時点で削除し、各 app に責務を説明する README を置く。

## Build, Test, and Development Commands
- 開発環境: ルートで `nix develop`（flake で JDK 25 / sbt / Node / Rust を固定。Scala 3.8 系は JDK 17+ 必須なので JDK は nix で揃える）。以降のコマンドは dev shell 内で実行する。
- API(Scala): sbt ビルドルートは `apps/api`。`cd apps/api` してから `sbt compile` / `sbt test`。純粋ドメインだけは `sbt domain/test`。`api/run` 等は今後追加。
- RMU(Rust): `cd apps/rmu` で `cargo build` / `cargo test`（実装はタスク3以降）。
- UI: `apps/ui` で `pnpm install`、各 app で `pnpm dev` / `pnpm build` / `pnpm test` / `pnpm lint`。
- 型生成: API の OpenAPI から `packages/api-client` を再生成（`pnpm gen:api`）。
- リポジトリ全体のエントリは将来 root の `Makefile`（例 `make dev-api` / `make test-ui`）に集約する。

## Coding Style & Naming Conventions
- Scala 3: scalafmt で整形。ドメインは純粋に保ち、IO は infrastructure に閉じる（ヘクサゴナル）。
- TypeScript: Prettier + ESLint（`--max-warnings=0`）。2-space、single quote、trailing comma。
- 命名: 関数 `camelCase`、型/コンポーネント/クラス `PascalCase`。
  - ファイル: **Scala は主要な型に対応する `PascalCase`**（例 `Article.scala`）。**TS / ディレクトリは `kebab-case`**。
- 環境変数は各 app にスコープし、`.env.local`（git-ignored）。

## Testing Guidelines
- Scala: **zio-test**。ドメイン（値オブジェクト・不変条件、後にコマンド側集約）を重点的に。投影 fold の検証は RMU(Rust) 側。spec は対象モジュール配下。
- TS: ユニットは `*.spec.ts` を co-locate、`apps/ui/web/<app>/tests/integration` に統合テスト。
- マージ前に高速なユニットを通す。意図的なギャップは PR に記載。

## Commit & Pull Request Guidelines
- Conventional Commits（`type(scope): summary`）。
- PR には概要・関連 issue・レビュー用セットアップ手順・UI 変更はスクリーンショットを含める。
- ワークフローを変えたら本 AGENTS.md も更新する。
- GitHub Actions（`.github/workflows/`）で参照する Action は**コミット SHA で固定**（末尾コメントにバージョン目安）。

## 自動レビュー（codex）
- 全 PR は `.github/workflows/codex-review.yml`（公式 `openai/codex-action`）で **codex の自動レビューが必須実行**され、結果が PR にスティッキーコメントとして残る。
- レビュー観点: `docs/design.md`・`docs/slice-1-plan.md` との整合 / 正しさ / セキュリティ / スコープ逸脱。
- 必要な設定: リポジトリ Secret **`OPENAI_API_KEY`**（未設定だと CI は失敗する＝レビューを黙ってスキップしない）。
- ジョブは review（`contents:read` で codex 実行）と post（`pull-requests:write` でコメント投稿）に分離し、codex 実行中は PR 書込権限を渡さない。
