# Repository Guidelines

設計の全体像は `docs/design.md`、構成図は `docs/architecture.html` を参照。本ファイルは貢献者 / エージェント向けの作業ガイド。

## Project Structure & Module Organization
モノレポ。コードは `apps/` 配下に集約（リポジトリ直下は `docs/` と meta のみ）。Scala バックエンドは **単一 sbt ビルド**（ビルドルート=`apps/api`、`apps/api/build.sbt` + `apps/api/project/`）、UI は **pnpm workspace**。dev 環境はルートの `flake.nix`（`nix develop`）。

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
- API(Scala): sbt ビルドルートは `apps/api`。`cd apps/api` してから `sbt compile` / `sbt test`。集約カバレッジ取得は `sbt clean` 後に `sbt coverage domain/test application/test infrastructure/test coverageAggregate`（HTML は `target/scala-*/scoverage-report/`）。純粋ドメインだけは `sbt domain/test`。`api/run` 等は今後追加。
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

### 開発手法: t-wada 流 TDD（Red → Green → Refactor）
実装はテスト駆動で進める。プロダクションコードより先に**失敗するテスト**を書く。
1. **テストリスト**: 着手前にテストすべき仕様を箇条書きで洗い出す（網羅でなくてよい・随時追加）。一度に **1 項目だけ** 着手。
2. **Red**: 次の 1 項目について失敗するテストを書き、**期待通りに失敗する**こと（コンパイルエラーでなく assertion で、正しい理由で落ちる）を実行して確認する。
3. **Green**: 最短でテストを通す。状況で手段を使い分ける —
   - **仮実装（Fake It）**: 定数ベタ書きで通す（不安なとき）。
   - **三角測量（Triangulation）**: 2 つ目以降のケースを足して一般化を強制（設計が見えないとき）。
   - **明白な実装**: 自明なら正しい実装を直接書く。
4. **Refactor**: **Green のまま**重複除去・命名改善・構造整理（テストが安全網）。
- **歩幅の調整**: 不安なら小さく（仮実装→三角測量）、自信があれば大きく（明白な実装）。
- テスト名は**仕様**を表す。アサーションから書いて逆算する（assert first）。
- コミットは少なくとも Green になってから（Red/Green/Refactor の節目で意味のある単位に）。

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

### PR 後の bot レビュー triage（エージェント運用ルール）
- **PR を作成したら、bot の自動レビュー（codex sticky / `chatgpt-codex-connector` 等）が出揃うまで待ち、内容を確認・評価するところまで自動で行う。** 手順は `$triage-pr-reviews`（`.agents/skills/triage-pr-reviews/SKILL.md`）に集約。
- bot レビューは非同期（CI 数分）。HEAD 向けレビューが出るまでポーリングして待つ。
- 各指摘は現行 HEAD と突き合わせて「要対応／対応不要（設計意図・陳腐化）／曖昧」に分類。**評価の提示までが自動で、実際のコード修正・push・PR への返信はユーザー承認後**に行う。
