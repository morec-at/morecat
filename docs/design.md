# morecat v1 — 設計合意（確定版）

個人 CMS「morecat」の v1 設計ドキュメント。ブログ記事と自己紹介ページを公開する。

## 0. 方針

craft（Scala / ZIO / DDD・自前 API を学び楽しむ）と実用（自分のブログとして運用）の**両立**。
インフラは **GCP / Firebase に一貫**させ、Cloud Run 等を学習対象とする。過剰な部分は削り、最終的に実運用へ乗せる。

---

## 1. コンテンツモデル（ヘクサゴナルの domain）

`Article` と `Page` の 2 型に分ける。

### Article（ブログ記事）
| フィールド | 型 / 制約 |
|---|---|
| `id` | UUIDv7（アプリ採番・時系列ソート可） |
| `slug` | unique, 手入力必須 + 自動サジェスト, 公開後は原則不変 |
| `title` | 非空 |
| `body` | Markdown(GFM) を生保存 |
| `tags` | N:N（正規化） |
| `status` | `draft` / `published` |
| `publishedAt` | nullable |
| `createdAt` / `updatedAt` | timestamp |

### Page（独立ページ。例 `/about`）
`id: UUIDv7`, `slug`, `title`, `body`(Markdown), `status`(draft/published), timestamps。

### Tag
`id`, `name`, `slug` ＋ `article_tags` 中間テーブルで正規化（N:N）。

### ドメイン表現
- 値オブジェクトは **Iron** で精緻化（Slug / NonEmptyTitle 等）+ smart constructor
- Repository は domain 層の **port (trait)**。実装は infrastructure 層

---

## 2. コンポーネント & ホスティング（全て GCP / Firebase）

| 役割 | 技術 | ホスト |
|---|---|---|
| API | Scala 3 + ZIO + **tapir**（zio-http backend, OpenAPI 生成）。層: domain(純粋) / application(ユースケース) / infrastructure(Postgres リポジトリ実装・tapir エンドポイント・GCS クライアント)、ZLayer 配線 | **Cloud Run** |
| DB | PostgreSQL、**Flyway** 素 SQL マイグレーション | **Cloud SQL** |
| viewer（公開） | Next.js SSR + **ISR 時間再検証** + 公開時 **on-demand revalidate webhook** | **Firebase App Hosting** |
| editor（管理） | Vite + React SPA | **Firebase Hosting** |
| メディア | v1 は **API プロキシ入稿**（検査 / WebP 変換 / EXIF 除去）→ 公開読み取り | **GCS** |
| CUI | — | v1 ドロップ（ディレクトリは維持） |

---

## 3. 認証・ドメイン

- **GitHub OAuth**（自アカウントを allowlist）→ API が **ステートレス署名 Cookie**（JWT / PASETO in Cookie, httpOnly + Secure + SameSite=Lax）を発行
- Cloud Run はゼロスケール・複数インスタンスのため、セッション実体は持たずステートレス Cookie に格納
- 独自ドメイン + サブドメイン構成:
  - `www` / apex … viewer
  - `admin.*` … editor
  - `api.*` … API
  - Cookie は `Domain=.example`（サブドメイン間共有, SameSite=Lax）
- メディアバケットは**非公開書き込み**・公開読み取り（プロキシ方式なので CORS 直 PUT 不要）

---

## 4. 型契約（API ↔ フロント）

tapir → **OpenAPI** → `openapi-typescript` → **pnpm workspace の `packages/api-client`** を viewer / editor が共有。
エンドツーエンドで型安全。

---

## 5. レンダリング

- 本文は Markdown(GFM) を生保存、**描画時に HTML 化**（viewer 側 remark / rehype）
- **rehype-sanitize（拡張 allowlist：信頼ホストの iframe 等のみ許可）**
- コードシンタックスハイライト（rehype 系プラグイン）

---

## 6. URL（パーマリンク）

```
/                  一覧
/posts/[slug]      記事
/[pageSlug]        Page（例 /about）
/tags/[tag]        タグ絞り込み
/feed.xml          RSS/Atom
/sitemap.xml       サイトマップ
/robots.txt        クローラ制御
```

---

## 7. viewer v1 機能スコープ

- OGP / メタタグ（SEO 基礎）
- sitemap.xml + robots.txt
- RSS / Atom フィード
- コードシンタックスハイライト

**v2 以降に延期**: 全文検索, コメント。

---

## 8. 執筆ワークフロー

editor で執筆 → **Next.js Draft Mode**（署名トークン URL, 検索除外）で本番同等プレビュー → 公開で revalidate webhook 発火 → viewer 即時反映。

---

## 9. CI/CD

**全て GitHub Actions**（モノレポ path フィルタで変更部分のみビルド）。

- API … コンテナビルド → Artifact Registry → Cloud Run デプロイ
- editor … `firebase deploy`（Firebase Hosting）
- viewer … Actions から App Hosting ロールアウト駆動

> ⚠️ Firebase App Hosting は本来 GitHub git 連携でブランチ push を監視し自動ロールアウトする機構。Actions に一元化する場合は App Hosting Rollouts API / `firebase` CLI 経由で駆動する点に留意。

---

## 10. リポジトリ構成

```
apps/
  api/                      sbt マルチモジュール
    modules/
      domain/               純粋ドメイン（Iron 値オブジェクト, port）
      application/          ユースケース
      infrastructure/       Postgres 実装・tapir・GCS
      bootstrap/            ZLayer 配線・エントリポイント
  ui/
    web/
      viewer/               Next.js (Firebase App Hosting)
      editor/               Vite + React SPA (Firebase Hosting)
    cui/                    v1 ドロップ（プレースホルダ維持）
    packages/
      api-client/           OpenAPI 生成型 / クライアント（pnpm workspace 共有）
docs/
  design.md                 本ドキュメント
```

> 注: `apps/ui/packages/api-client` は pnpm workspace のルート設定に応じて配置を調整。

---

## 11. 定石で進める項目（推奨デフォルト・未確定の細部）

- JSON コーデック: **zio-json + iron** 連携
- テスト: **zio-test**
- 観測性: Cloud Logging / Monitoring 既定（Sentry 等のエラートラッキングは v2）
- GCS レイアウト: `images/yyyy/mm/<uuid>.webp`、ライフサイクルルールで孤児オブジェクト GC
- revalidate webhook / Draft プレビュートークンは共有シークレットで保護
- 独自ドメイン取得は別途必要
- API エンドポイント表面（CRUD: articles / pages / tags / media upload / auth / revalidate）は実装時に OpenAPI として確定

---

## 付録: 主要な設計判断の理由

- **viewer = Next.js SSR**: SEO を効かせたい。App Hosting は Next.js 専用設計で裏は Cloud Run のため GCP 学習とも両立。
- **editor = 軽量 SPA**: 管理面に SSR は不要。SSG/SEO 不要なので Vite で軽量に。
- **API プロキシ入稿（署名 URL 直 PUT ではなく）**: 書き手 1 人＝アップロード量が極小で、署名 URL の利点（バイト肩代わり）が薄い。プロキシなら検査 / WebP 変換 / EXIF 除去をサーバーで一元化でき、CORS・孤児管理の摩擦もない。署名 URL 直 PUT は規模拡大時の発展形。
- **ステートレス署名 Cookie**: Cloud Run のゼロスケール・複数インスタンスと相性最良。シングルユーザーなら失効管理も軽い。
- **正規化タグ**: タグ一覧 / 件数集計 / リネーム / `/tags/xxx` ページが素直。
- **UUIDv7**: domain が DB 往復なしにエンティティを生成でき、ヘクサゴナルの自律生成と相性が良い。時刻順で index 効率も良好。
