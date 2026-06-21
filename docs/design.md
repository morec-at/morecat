# morecat v1 — 設計合意（確定版）

個人 CMS「morecat」の v1 設計ドキュメント。ブログ記事と自己紹介ページを公開する。

## 0. 方針

craft（Scala / ZIO / DDD・自前 API を学び楽しむ）と実用（自分のブログとして運用）の**両立**。
インフラは **GCP / Firebase に一貫**させ、Cloud Run 等を学習対象とする。過剰な部分は削り、最終的に実運用へ乗せる。

---

## 1. コンテンツモデル（ヘクサゴナルの domain）

`Article` と `Page` の 2 型に分ける。
**Article は Event Sourcing（フル ES、イベントが唯一の真実）**、**Page は state 保存（CRUD）**で扱う（詳細は §1bis）。

### Article（ブログ記事・Event Sourced）
集約は Article。読み取りモデル（プロジェクション）として以下を Cloud SQL に持つ。
| フィールド | 型 / 制約 |
|---|---|
| `id` | UUIDv7（アプリ採番・時系列ソート可、集約 ID） |
| `slug` | unique, 手入力必須 + 自動サジェスト, 公開後は原則不変 |
| `title` | 非空 |
| `body` | Markdown(GFM) を生保存 |
| `tags` | N:N（正規化） |
| `status` | `draft` / `published` |
| `publishedAt` | nullable |
| `createdAt` / `updatedAt` | timestamp |
| `lastAppliedSeq` | プロジェクション適用済みシーケンス（RMU の冪等性用） |

**ドメインイベント（中粒度・意図/コマンド整合）**
- `ArticleDrafted` — 下書き作成（初期 slug/title/body/tags を含む）
- `ArticleRevised` — 保存で変わった title/body/tags/slug を含む（インプレース編集）
- `ArticlePublished` — 公開（publishedAt）
- `ArticleUnpublished` — published → draft（記事を下げる）

公開済み記事の編集は `ArticleRevised` でインプレース反映（revalidate 後に反映）、Unpublish も許可（2 状態は維持）。

### Page（独立ページ。例 `/about`・CRUD）
`id: UUIDv7`, `slug`, `title`, `body`(Markdown), `status`(draft/published), timestamps。Cloud SQL に state 保存。

### Tag
`id`, `name`, `slug` ＋ `article_tags` 中間テーブルで正規化（N:N）。Cloud SQL の読み取りモデル側。

### ドメイン表現
- 値オブジェクトは **Iron** で精緻化（Slug / NonEmptyTitle 等）+ smart constructor
- Repository は domain 層の **port (trait)**。実装は infrastructure 層
- Article の Repository は**イベントソース型**: load = Firestore のイベントストリームを fold、save = 期待バージョン（sequence）で新イベントを append

---

## 1bis. Article の Event Sourcing / CQRS

DynamoDB Streams 型の「ストアへ書込→自動で購読者へ通知（CDC）」を、GCP ネイティブ・サーバレス・低コストで実現する。**outbox パターンは使わない。**

```
[API (Cloud Run)] --append event + slug 予約 (同一 Firestore Tx)--> [Firestore (Native) = イベントストア]
                                            |
                                     doc 作成イベント
                                            v
                                     [Eventarc トリガ]
                                            |
                                     push (at-least-once / 順不同)
                                            v
                               [RMU = Read Model Updater (別 Cloud Run サービス)]
                                            |
                                  fold して upsert
                                            v
                              [Cloud SQL (Postgres) = 読み取りモデル]
                                            |  ^
                          upsert 完了後 revalidate |  | query
                                            v  |
                              [viewer] <----+   [API 読み取り側]
```

### コマンド側（書込）
- API が Article イベントを **Firestore(Native)** に append
- イベントレイアウト: サブコレクション `articles/{articleId}/events/{seq}`
- **楽観ロック**: doc ID = `seq`（= 期待バージョン）の **create-only**。既存なら並行競合として失敗
- **slug 一意性は受付時に保証**（読取モデル任せにしない）。`slugs/{slug}`（doc id = slug, value = articleId）を**イベント append と同一 Firestore トランザクション**で create-only 予約。Firestore Tx は強整合なので、コマンド受付時点で公開 URL の一意性を担保。イベント doc と slug doc を 1 Tx で原子的に書くため**追加ストア不要・二重書き込みなし**
  - slug 変更（`ArticleRevised`）時は同 Tx で旧予約を解放し新 slug を予約
  - 衝突時は **409 / ドメインエラー**をコマンド受付で返す

### 通知（CDC）
- **Firestore + Eventarc トリガ**で、イベント doc 作成時に **RMU(Cloud Run)** を自動起動
- ゼロスケール・サーバレス。Eventarc は **at-least-once・順序保証なし**

### RMU（読み取りモデル更新）
- **Rust on Cloud Run**（ネイティブ単一バイナリ・高速コールドスタート）。craft / 多言語実験を兼ねた学習対象。axum で Eventarc(CloudEvent) を受け、Firestore 読取・Postgres 書込は Rust クレート（`firestore`/`gcloud-sdk`, `sqlx`）。**ドメインのコードは API と共有しない**（cross-compile しない）。イベントの wire スキーマのみ**契約フィクスチャ**で Scala と整合させ、`fold` は Rust 側に再実装する
- トリガ受信時、その `articleId` の**全イベントストリームを Firestore から再読込して fold**し、プロジェクションを再構築して **Cloud SQL に upsert**
- 順序無関係・冪等・欠落耐性。記事あたりイベントは少なく安価。`lastAppliedSeq` で古い適用を skip
- **revalidate は RMU が upsert 完了後に発火**（重要）。読取モデルを更新した側が viewer の on-demand revalidate を起こすことで、viewer 再生成時に投影が最新であることを保証（read-your-write 成立）。API からの直接 revalidate は行わない
- **リプレイ/再構築**: Cloud Run job/CLI で全 Firestore イベントを読み Cloud SQL プロジェクションを作り直す

### トレードオフ（明示）
- Eventarc は順序保証なし → 上記「全ストリーム再読込 fold」で吸収（DynamoDB Streams のキー単位順序保証は持たない）
- 厳密な順序付き CDC が必要なら Cloud Spanner change streams（高コスト）か DynamoDB+Streams（GCP 外）になるが、本件はコスト/GCP 学習を優先し Firestore+Eventarc を採用

## 2. コンポーネント & ホスティング（全て GCP / Firebase）

| 役割 | 技術 | ホスト |
|---|---|---|
| API | Scala 3 + ZIO + **tapir**（zio-http backend, OpenAPI 生成）。層: domain(純粋) / application(ユースケース) / infrastructure(Firestore イベントリポジトリ・Postgres 読取リポジトリ・tapir エンドポイント・GCS クライアント)、ZLayer 配線。書込=Firestore / 読取=Cloud SQL | **Cloud Run** |
| Article イベントストア | Firestore(Native)。`articles/{id}/events/{seq}`、create-only 楽観ロック | **Firestore** |
| CDC 通知 | Firestore doc 作成 → **Eventarc** トリガ | **Eventarc** |
| RMU（読取モデル更新） | **Rust**（axum + serde + sqlx + Firestore クレート）。イベントを fold して Cloud SQL に upsert（冪等・順序無関係）。ネイティブバイナリで高速コールドスタート。ドメインは API と**コード共有せず**、wire スキーマを契約フィクスチャで整合。**upsert 完了後に viewer へ revalidate 発火** | **Cloud Run**（API とは別サービス） |
| 読み取りモデル / Page / Tag | PostgreSQL、**Flyway** 素 SQL マイグレーション | **Cloud SQL** |
| viewer（公開） | Next.js SSR + **ISR 時間再検証** + **RMU からの on-demand revalidate**（投影更新後） | **Firebase App Hosting** |
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

editor で執筆 → **Next.js Draft Mode**（署名トークン URL, 検索除外）で本番同等プレビュー → 公開コマンド（API→Firestore append）→ Eventarc→**RMU が Cloud SQL を更新し、完了後に viewer へ revalidate** → viewer 反映。
revalidate を RMU 側に置くことで、「投影が古いまま viewer が再生成して stale をキャッシュする」競合を回避する。

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
flake.nix                   dev 環境（JDK25 / sbt / Node / Rust）。nix develop
flake.lock
apps/                       コード（言語ごとに独立ビルド）
  api/                      JVM deployable: tapir HTTP サーバ（Cloud Run）。sbt ビルドルート
    build.sbt / project/    単一 sbt ビルド（api のサブプロジェクト群を集約）
    domain/                 純粋サブプロジェクト: Article イベント ADT・値オブジェクト(Iron)・projection fold。IO や wire フォーマット(JSON 等)を持たない
    application/            ユースケース（コマンド/クエリ）
    infrastructure/         Firestore(JVM SDK)・Postgres・GCS・tapir
    bootstrap/              ZLayer 配線・エントリポイント
  rmu/                      Rust deployable: Eventarc 起動の RMU（Cloud Run）。Cargo
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

- 言語ごとに独立ビルド: `apps/api`(sbt) / `apps/rmu`(Cargo) / `apps/ui`(pnpm)。リポジトリ直下はビルド定義を持たない（dev 環境は root の `flake.nix`）
- API は単一 sbt ビルド（ビルドルート=`apps/api`）。`domain`(純粋) <- `application` <- `infrastructure` <- `bootstrap` のサブプロジェクト構成。`domain` に IO 依存を載せないことをサブプロジェクト分割で強制
- **ドメインは API と RMU でコード共有しない**（cross-compile 廃止）。RMU(Rust) はイベントの wire スキーマのみ契約フィクスチャで Scala と整合させ、`fold` は Rust 側に再実装する
- 注: `apps/ui/packages/api-client` は pnpm workspace のルート設定に応じて配置を調整

---

## 11. 定石で進める項目（推奨デフォルト・未確定の細部）

- JSON コーデック: **zio-json + iron**（iron-zio-json）連携。**infrastructure 層**に置く（domain は wire フォーマットを知らない）
- テスト: **zio-test**
- 観測性: Cloud Logging / Monitoring 既定（Sentry 等のエラートラッキングは v2）
- GCS レイアウト: `images/yyyy/mm/<uuid>.webp`、ライフサイクルルールで孤児オブジェクト GC
- revalidate webhook / Draft プレビュートークンは共有シークレットで保護
- 独自ドメイン取得は別途必要
- API エンドポイント表面は実装時に OpenAPI として確定。Article は**コマンド側**（draft 作成 / revise / publish / unpublish → Firestore へ append）と**クエリ側**（一覧 / slug / tags → Cloud SQL）に分離。Page / media upload / auth / revalidate は別途
- **イベントスキーマのバージョニング**: イベント payload に `schemaVersion` を持たせ、fold 時にアップキャスト
- **Firestore セキュリティ**: イベントストアへの書込は API のサービスアカウントのみ（クライアント直書き禁止）。Eventarc は RMU 失敗時リトライ + DLQ を設定
- RMU は公開エンドポイントにせず、Eventarc / 当該サービスアカウントからの呼び出しのみ許可（Cloud Run IAM）

---

## 付録: 主要な設計判断の理由

- **viewer = Next.js SSR**: SEO を効かせたい。App Hosting は Next.js 専用設計で裏は Cloud Run のため GCP 学習とも両立。
- **editor = 軽量 SPA**: 管理面に SSR は不要。SSG/SEO 不要なので Vite で軽量に。
- **API プロキシ入稿（署名 URL 直 PUT ではなく）**: 書き手 1 人＝アップロード量が極小で、署名 URL の利点（バイト肩代わり）が薄い。プロキシなら検査 / WebP 変換 / EXIF 除去をサーバーで一元化でき、CORS・孤児管理の摩擦もない。署名 URL 直 PUT は規模拡大時の発展形。
- **ステートレス署名 Cookie**: Cloud Run のゼロスケール・複数インスタンスと相性最良。シングルユーザーなら失効管理も軽い。
- **正規化タグ**: タグ一覧 / 件数集計 / リネーム / `/tags/xxx` ページが素直。
- **UUIDv7**: domain が DB 往復なしにエンティティを生成でき、ヘクサゴナルの自律生成と相性が良い。時刻順で index 効率も良好。
- **Article = フル ES**: craft / 学習目的。イベントを唯一の真実とし、CQRS で読み取りモデルを分離。Page は変更履歴の要求が薄いので CRUD のまま据え置き（混在を許容）。
- **イベントストア = Firestore（EventStoreDB / Kurrent ではなく）**: 当初 ESDB を検討したが、マネージド（Kurrent Cloud）は月 $100〜210 と個人ブログに過剰、セルフホストは月 $16〜30 でも常時起動 VM が Cloud Run 中心の構成からはみ出す。Firestore はサーバレス・低コストで、次項の CDC と一体で機能する。
- **CDC = Firestore + Eventarc → Cloud Run RMU（outbox なし）**: DynamoDB Streams 型の「書込→自動通知」を GCP ネイティブで実現する最短解。serverless・ゼロスケール。代償は順序保証がないことだが、RMU を「全ストリーム再読込 fold」にして吸収。Spanner change streams は順序保証ありだが高コスト、DynamoDB は GCP 外になるため見送り。
- **RMU = トリガごとに全ストリーム fold**: Eventarc の at-least-once・順不同に対し、最も単純で堅牢。記事あたりイベント数が少ないため再読込コストは無視できる。`lastAppliedSeq` で古い適用を skip。
- **RMU = Rust（JVM/Scala.js ではなく）**: ephemeral な購読者として都度起動するため JVM のコールドスタート（数秒）を避けたい。当初は Scala.js→Node を検討（高速起動＋`domain` を cross-compile で API と共有）したが、(1) RMU の責務は小さく、(2) morecat は craft / 多言語実験を主目的とし、(3) コード共有(fold)の単一ソース性は優先度が低い、との判断から **Rust** を採用。ネイティブ単一バイナリで最速級のコールドスタート、薄い consumer なので学習対象として最適。共有が要るのは**イベントの wire スキーマ**のみで、契約フィクスチャ（`events.json`→`expected-projection.json`）を Scala / Rust 双方のテストで検証して drift を防ぐ。`fold` は Rust に再実装（Scala の fold は参照実装）。結果として Scala(api) は単一 sbt ビルド、rmu は独立 Cargo ビルドとなり、言語ごとに独立した deployable になる。
- **revalidate を RMU 側に置く（API ではなく）**: 投影更新が非同期（Eventarc→RMU）なので、API が公開時に直接 revalidate すると、投影更新前に viewer が再生成して stale をキャッシュしうる。読取モデルを更新した RMU が upsert 完了後に revalidate を起こせば read-your-write が成立。PR レビュー（emag）指摘への対応。
- **slug 一意性をコマンド受付時に保証**: slug unique を Cloud SQL 投影だけに頼ると、同一 slug の並行 append が RMU upsert 時にしか衝突を検出できない。Firestore は Tx が強整合なので、`slugs/{slug}` 予約をイベント append と同一 Tx で行い受付時に一意性を担保（単一ストアで二重書き込みも回避）。PR レビュー（emag）指摘への対応。
