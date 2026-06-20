# スライス1 実装計画（合意版）

`docs/design.md` の確定設計に沿って、morecat を **薄い縦切り（walking skeleton）を1本** 貫通させる最初のスライスの実装計画。到達点は **本番デプロイ済み**で End-to-End が動くこと。

## 0. 方針

ES + CQRS + cross-compile domain + 4 デプロイ単位（api / rmu / viewer / editor）と構成要素が多いため、最初に薄い縦切りを End-to-End で一度貫通させ、「イベントが流れて読取モデルが更新される」を実物で確認してから各コンポーネントを肉付けする。

## 1. 確定した方針

| 論点 | 決定 |
|---|---|
| 進め方 | **薄い縦切り**を1本貫通。到達点は**本番デプロイ済み** |
| ローカル環境 | Firestore エミュレータ + Postgres(Docker)。Eventarc は本番のみ、ローカルは **realtime listener の CDC ブリッジ**（RMU の契約 `articleId` 受領→全ストリーム再 fold は本番と同一に保つ） |
| 認証 | **境界を初日に**（`Authenticator` port + tapir security middleware で全コマンド endpoint をゲート）。機構は **Secret Manager の Bearer トークン**1本。GitHub OAuth + 署名 Cookie は editor 導入スライスで `Authenticator` 実装を差し替え |
| slug 予約 Tx | **OUT**（スライス2）。まず slug は列に書くだけ |
| viewer データ経路 | **viewer → API の HTTP クエリのみ**。DB に触るのは API だけ（ヘクサゴナル境界を維持、§4 型契約の土台） |
| Postgres アクセス(JVM) | **Magnum + `magnum-zio`**（ZIO ネイティブ `connect`/`transact`、SQL 直書き、メンテ継続中） |
| プロビジョニング/デプロイ | **Terraform + GitHub Actions を最初から**。CI は build+test+**本番自動デプロイ**まで |

### バージョン（デフォルト）

- Scala **3.8.x** / sbt **1.10.x**（プラグイン成熟優先で 1.x）
- ZIO **2.1.x** / tapir **1.11.x**（zio-http backend, OpenAPI 生成）
- Iron **2.x** + zio-json（iron 連携）
- Scala.js **1.18.x**（RMU）
- Flyway **10/11.x**（素 SQL マイグレーション）
- Magnum **1.3.x**（`magnum-zio`）
- UUIDv7: 軽量ライブラリ（`java-uuid-generator` 等）かドメインで自前生成

## 2. スコープ（IN / OUT）

### IN
- domain: イベントは `ArticleDrafted` のみ。`Slug` / `NonEmptyTitle`（Iron + smart constructor）、`fold`、zio-json codec、`schemaVersion`
- API: コマンド `POST /articles`（Firestore に seq=1 を **create-only 楽観ロック** で append）／クエリ `GET /articles/{slug}`（Postgres から）／認証境界 + Bearer
- RMU: `articleId` 受領 → 全ストリーム再読込 → fold → Postgres upsert
- viewer: `/posts/{slug}` を SSR で API 経由 1 件表示（**ISR/revalidate なし**、毎回ライブ読み）
- Postgres: articles 投影テーブルの Flyway マイグレーション1本
- インフラ: Terraform でリソース宣言、GitHub Actions で build+test+本番自動デプロイ
- テスト: **並行競合テスト**（seq=1 二重 append の失敗）+ domain の fold / VO 不変条件

### OUT（後続スライスへ）
- slug 予約 Tx（受付時の一意性担保）→ スライス2
- publish / unpublish / revise、status ワークフロー
- 認証の最終形（GitHub OAuth + 署名 Cookie + allowlist）
- tags / Page / media(GCS)
- revalidate webhook（viewer は毎回ライブ読み）
- OpenAPI → `packages/api-client` 生成（最初は手書き fetch）

## 3. 着手順と各タスクの完了条件（DoD）

順序の眼目: 純粋 domain は全依存の土台かつ単体テストで速く固まる。次にローカル貫通で正しさを確認し、最後に Terraform/CI でデプロイ面を被せて、アプリの正しさとインフラ問題を切り分ける。

1. **`modules/domain`（crossProject .jvm / .js）**
   - `ArticleDrafted`、`Slug` / `NonEmptyTitle`（Iron + smart constructor）、`fold`、zio-json codec、`schemaVersion`
   - *DoD*: zio-test で fold と VO 不変条件が緑（jvm / js 両方コンパイル）

2. **`apps/api`（JVM, Cloud Run）**
   - Firestore append（doc id = seq の create-only 楽観ロック）／`Authenticator` port + Bearer middleware／`GET /articles/{slug}`（Magnum + magnum-zio で Postgres 読取）／tapir + zio-http
   - *DoD*: エミュレータで draft 作成 → Firestore に seq=1。トークン無し/不正 = 401。**seq=1 二重 append が並行競合で失敗するテスト**が緑

3. **`apps/rmu`（Scala.js → Node, Cloud Run）**
   - Eventarc / ブリッジ受信 → 全ストリーム再読込 fold → Postgres upsert（`lastAppliedSeq` で古い適用を skip）。Firestore / pg は Node facade
   - *DoD*: `articleId` POST で Postgres に1行 upsert。冪等（再送で重複しない）

4. **ローカル貫通**
   - CDC ブリッジ（emulator listener → RMU）で `POST /articles` → Firestore → RMU → Postgres → `GET /articles/{slug}` → viewer `/posts/{slug}` SSR 表示
   - *DoD*: ローカルで 1〜4 が一気通貫

5. **Terraform + GitHub Actions**
   - Firestore / Cloud SQL / Cloud Run×2 / Eventarc / App Hosting / Artifact Registry / Secret Manager / IAM を宣言。Actions: `sbt test` → コンテナ build → push → Cloud Run / App Hosting デプロイ
   - *DoD*: 本番でも貫通。CI 緑で自動デプロイ

## 4. 既知のリスク（実装時に注意・現時点で決定不要）

- **Eventarc × Firestore サブコレクション**: `articles/{id}/events/{seq}` への collection-group 的トリガ指定が素直に効くか要検証。効かなければ trigger のパスパターン設計を調整
- **Cloud Run → Cloud SQL 接続**: Cloud SQL Connector / Unix socket の配線
- **RMU の IAM**: Eventarc / 指定サービスアカウントからのみ呼出可（公開エンドポイントにしない）
