# スライス1 実装計画（合意版 / codex レビュー反映済み）

`docs/design.md` の確定設計に沿って、morecat を **薄い縦切り（walking skeleton）を1本** 貫通させる最初のスライスの実装計画。到達点は **本番デプロイ済み**で End-to-End が動くこと。

> 改訂履歴: 初版を作成後、codex レビュー（PR #81）の High/Medium/Low 指摘に全面同意し改訂。主な変更 — slug 予約 Tx を IN に戻す / `ArticlePublished` を IN に追加し公開境界を明示 / Bearer 運用・IAM 最小権限・WIF・Cloud SQL/シークレット運用を DoD 化 / 最小 OpenAPI 生成を IN に / 入力検証・RMU 失敗時運用・revalidate 移行条件を追記。

## 0. 方針

ES + CQRS + cross-compile domain + 4 デプロイ単位（api / rmu / viewer / editor）と構成要素が多いため、最初に薄い縦切りを End-to-End で一度貫通させ、「イベントが流れて読取モデルが更新される」を実物で確認してから各コンポーネントを肉付けする。ただし**設計の核（slug 一意性の受付時保証・公開/下書き境界・認可境界）は walking skeleton でも省略しない**（codex High 指摘）。

## 1. 確定した方針

| 論点 | 決定 |
|---|---|
| 進め方 | **薄い縦切り**を1本貫通。到達点は**本番デプロイ済み** |
| ローカル環境 | Firestore エミュレータ + Postgres(Docker)。Eventarc は本番のみ、ローカルは **realtime listener の CDC ブリッジ**（RMU の契約 `articleId` 受領→全ストリーム再 fold は本番と同一に保つ） |
| 認証 | **境界を初日に**（`Authenticator` port + tapir security middleware で全コマンド endpoint をゲート）。機構は **Secret Manager の Bearer トークン**1本。運用要件（生成/失効/ローテーション/漏洩防止/平文非保持）を DoD 化。GitHub OAuth + 署名 Cookie は editor 導入スライスで `Authenticator` 実装を差し替え |
| slug 予約 Tx | **IN**（設計 §1bis）。`slugs/{slug}` create-only をイベント append と**同一 Firestore Tx**で予約し、受付時に一意性を担保。衝突は **409** |
| 公開/下書き境界 | **IN**。`ArticleDrafted`（下書き作成）＋ `ArticlePublished`（公開）。公開系の読取（query / viewer）は **published のみ**返し、draft / 不在は **404** |
| viewer データ経路 | **viewer → API の HTTP クエリのみ**。DB に触るのは API だけ（ヘクサゴナル境界を維持、§4 型契約の土台） |
| Postgres アクセス(JVM) | **Magnum + `magnum-zio`**（ZIO ネイティブ `connect`/`transact`、SQL 直書き、メンテ継続中） |
| 型契約 | `POST /articles`・publish・`GET /articles/{slug}` の**最小 OpenAPI を tapir から生成**し `packages/api-client` を生成（§4。手書き fetch による二重管理を避ける） |
| プロビジョニング/デプロイ | **Terraform + GitHub Actions を最初から**。CI は build+test+**本番自動デプロイ**まで。GCP 認証は **Workload Identity Federation**（静的キー禁止）、本番は protected environment |

### バージョン（デフォルト）

- Scala **3.8.x** / sbt **1.10.x**（プラグイン成熟優先で 1.x）
- ZIO **2.1.x** / tapir **1.11.x**（zio-http backend, OpenAPI 生成）
- Iron **2.x** + zio-json（iron 連携）
- Scala.js **1.18.x**（RMU）
- Flyway **10/11.x**（素 SQL マイグレーション）
- Magnum **1.3.x**（`magnum-zio`）
- UUIDv7: 軽量ライブラリ（`java-uuid-generator` 等）かドメインで自前生成

> 注: 上記は最新/将来系を含むため、**最初のタスクの DoD に「sbt / scalafmt / tapir / zio / Scala.js / Iron / Magnum の組み合わせが実際に解決・コンパイルできること」の検証を含める**（codex Low 指摘）。

## 2. スコープ（IN / OUT）

### IN
- **domain**: イベントは `ArticleDrafted` ＋ `ArticlePublished`。`Slug` / `NonEmptyTitle`（Iron + smart constructor）、`fold`（`status` を含む投影）、zio-json codec、`schemaVersion`
- **API（コマンド）**:
  - `POST /articles`（draft 作成）— Firestore に seq=1 を **create-only 楽観ロック** で append ＋ `slugs/{slug}` 予約を**同一 Tx**で実施。衝突は **409**
  - publish コマンド `POST /articles/{id}/publish`（`ArticlePublished` を append）。**期待バージョン必須**（楽観ロック）、**未作成 id は 404**、**draft 以外（既公開）からの publish は冪等 or 409 として扱う**、`publishedAt` は**サーバ時刻で採番**（クライアント値を信頼しない）
  - 認証境界（`Authenticator` port + Bearer middleware）
  - **HTTP 境界の入力検証**: 400/409 の切り分け、body / Markdown サイズ上限、未知 JSON フィールド・`schemaVersion` 不一致の拒否、Firestore doc id として危険な slug 文字の拒否、SQL パラメータ化
- **API（クエリ）**: `GET /articles/{slug}` — Postgres から。**published のみ返却、draft / 不在は 404**。CORS 方針を明記
- **RMU**: `articleId` 受領 → 全ストリーム再読込 → fold → Postgres upsert（`lastAppliedSeq` で古い適用を skip）。**Eventarc payload 検証**: `articleId` の形式検証、対象パスが `articles/{id}/events/{seq}` であることの確認、`slugs/*` 等の非対象 doc を拒否（payload を鵜呑みにしない）。**失敗時運用**: リトライ上限・DLQ・無効 payload のログ・**手動 replay 導線（Job/CLI の最小版）**
- **viewer**: `/posts/{slug}` を SSR で API 経由 1 件表示（published のみ）。**本文 Markdown(GFM) → HTML 化時に `rehype-sanitize`（拡張 allowlist）で sanitization**（設計 §5。XSS を後続扱いにしない）。**ISR/revalidate なし**（毎回ライブ読み）
- **型契約**: 上記 endpoint の最小 OpenAPI 生成 → `packages/api-client`
- **Postgres**: articles 投影テーブル（`status`・`slug` unique 含む）の Flyway マイグレーション1本
- **インフラ**: Terraform でリソース宣言、GitHub Actions で build+test+本番自動デプロイ（IAM 最小権限・WIF・protected environment 含む。詳細は §4）
- **テスト**: domain の fold / VO 不変条件 ＋ **楽観ロック/原子性テスト群**（後述）

### OUT（後続スライスへ）
- `ArticleUnpublished` / `ArticleRevised`（公開後のインプレース編集・記事を下げる）→ 次スライス
- 認証の最終形（GitHub OAuth + 署名 Cookie + allowlist）
- tags / Page / media(GCS)
- revalidate webhook（viewer は毎回ライブ読み。**移行条件**は §5 に明記）
- 全文検索・コメント（設計 §7 で v2）

## 3. 着手順と各タスクの完了条件（DoD）

順序の眼目: 純粋 domain は全依存の土台かつ単体テストで速く固まる。次にローカル貫通で正しさを確認し、最後に Terraform/CI でデプロイ面を被せて、アプリの正しさとインフラ問題を切り分ける。

本スライスは実装量が大きいため、**2つのマイルストーンに分割**する（codex 指摘）。**マイルストーン A（タスク 1〜5）でローカル End-to-End 貫通**、**マイルストーン B（タスク 6）で本番デプロイ**。A 完了を中間ゲートとし、A が緑になってから B に進む。

### マイルストーン A — ローカル End-to-End 貫通

1. **`modules/domain`（crossProject .jvm / .js）**
   - `ArticleDrafted` / `ArticlePublished`、`Slug` / `NonEmptyTitle`（Iron + smart constructor）、`fold`（`status` 反映）、zio-json codec、`schemaVersion`
   - *DoD*: zio-test で fold と VO 不変条件が緑（jvm / js 両方コンパイル）。**ライブラリ組み合わせが解決・コンパイルできることを確認**

2. **`apps/api`（JVM, Cloud Run）**
   - Firestore append（doc id = seq の create-only 楽観ロック）＋ `slugs/{slug}` 予約を**同一 Tx**／publish コマンド／`Authenticator` port + Bearer middleware／`GET /articles/{slug}`（Magnum + magnum-zio、published のみ）／入力検証／tapir + zio-http → OpenAPI
   - *DoD*:
     - エミュレータで draft 作成 → 公開 → Firestore に append。トークン無し/不正 = 401
     - `GET` は published のみ返し draft / 不在 = 404
     - **テスト群**: ① seq=1 二重 append が並行競合で失敗 ② 期待バージョン不一致の append 失敗 ③ 同一 articleId の再送が冪等 ④ **slug 予約の原子性**（append 失敗時に slug 予約が残らない／同一 slug の競合は 409）
     - **publish 不変条件テスト**: 未作成 id は 404／draft 以外からの publish は冪等 or 409／期待バージョン不一致は失敗／`publishedAt` がサーバ時刻で採番される
     - 入力検証（サイズ上限・未知フィールド・危険な slug 文字）の拒否テスト

3. **`apps/rmu`（Scala.js → Node, Cloud Run）**
   - Eventarc / ブリッジ受信 → 全ストリーム再読込 fold → Postgres upsert（`lastAppliedSeq` で skip）。Firestore / pg は Node facade。**payload 検証**（`articleId` 形式・対象パス `articles/{id}/events/{seq}`・`slugs/*` 拒否）。失敗時運用（リトライ/DLQ/無効 payload ログ/手動 replay）
   - *DoD*: `articleId` POST で Postgres に upsert。冪等（再送で重複しない）。**不正な articleId / 非対象パスの payload を拒否**。**無効 payload は DLQ 行き＋ログ**。手動 replay（全 Firestore イベント → Postgres 再構築）の最小手順が動く

4. **`packages/api-client`（最小 OpenAPI 生成）**
   - API の OpenAPI から `POST /articles`・publish・`GET /articles/{slug}` の型 / クライアントを生成
   - *DoD*: viewer から生成クライアント経由で型安全に呼べる

5. **viewer + ローカル貫通**
   - viewer `/posts/{slug}`: SSR で api-client 経由取得 → Markdown(GFM) を **`rehype-sanitize`（拡張 allowlist）で sanitization** して HTML 化（published のみ）
   - CDC ブリッジ（emulator listener → RMU）で `POST /articles` → publish → Firestore → RMU → Postgres → `GET /articles/{slug}` → viewer `/posts/{slug}` SSR 表示
   - *DoD*: ローカルで 1〜5 が一気通貫。**悪意ある Markdown（`<script>`・`onerror` 等）が sanitize され描画されないテスト**

> **中間ゲート**: ここでマイルストーン A 完了。ローカルで End-to-End が緑になってから B（本番デプロイ）へ進む。

### マイルストーン B — 本番デプロイ

6. **Terraform + GitHub Actions（IAM / シークレット / CI 安全性込み）**
   - リソース宣言: Firestore / Cloud SQL / Cloud Run×2 / Eventarc / App Hosting / Artifact Registry / Secret Manager
   - **IAM 最小権限**: api / rmu / viewer 各サービスに**個別 SA**。**RMU の `run.invoker` は Eventarc 用 SA のみ**（公開エンドポイントにしない）。Firestore / Cloud SQL / GCS の権限を各 SA に最小付与
   - **Cloud SQL**: public IP 無効化、Connector / Unix socket 接続、**マイグレーション実行主体とアプリ実行主体の DB ユーザー分離**、接続情報は Secret Manager、**Terraform state に password を平文で持たせない**
   - **Bearer トークン**: Secret Manager 管理、Terraform state / GitHub Secrets に平文で持たせない、ログ/エラーへ出さない
   - **CI/CD 安全性**: GitHub Actions → GCP は **Workload Identity Federation**（静的 JSON キー禁止）、本番は **protected environment / approval**、Terraform apply の権限分離。App Hosting は Rollouts API / `firebase` CLI のどちらで駆動するか明記（preview / production の扱い）
   - *DoD*: 本番でも貫通。CI 緑で自動デプロイ。RMU が公開到達不可・Eventarc SA からのみ起動できることを確認

## 4. セキュリティ要件（横断・codex 指摘反映）

- **認可境界**: コマンド endpoint は Bearer 必須。公開クエリ（`GET /articles/{slug}`）は認証不要だが **published のみ・draft 非返却・不在は 404**
- **XSS / sanitization**: viewer は本文 HTML 化時に **`rehype-sanitize`（拡張 allowlist）必須**（設計 §5）。walking skeleton でも後続扱いにしない
- **シークレット**: Bearer トークン・DB 接続情報は Secret Manager。Terraform state / GitHub Secrets に平文を置かない。ログ漏洩防止。失効・ローテーション手順を残す
- **IAM 最小権限**: サービスごとに SA を分離。RMU は Eventarc SA からのみ invoke 可（公開しない）。さらに RMU は **Eventarc payload を信頼せず検証**（`articleId` 形式・対象パス・非対象 doc 拒否）
- **CI/CD**: Workload Identity Federation、静的キー禁止、本番は protected environment、apply 権限分離
- **後続に送る項目（理由を明記）**: Bearer は単一トークンで blast radius が大きく期限なしのため、**Cloud Logging での認証失敗監視・レート制限 / Cloud Armor** は後続スライスで導入（個人 CMS の slice 1 としては許容、導入条件を残す）。最終形は GitHub OAuth + 署名 Cookie への差し替え

## 5. revalidate 移行条件（OUT だが制約を明記）

スライス1の `viewer = 毎回ライブ読み（ISR/revalidate なし）` は簡略化。設計（§8）の「RMU upsert 後に revalidate」と意図的に異なるため、後続で ISR を導入する際は **revalidate を API ではなく RMU 側に置く**（投影更新後に発火）制約を守り、stale cache 競合を再導入しないこと。

> 注: `docs/design.md` / `docs/architecture.html` は `ISR + RMU revalidate` 前提で書かれているため、構成図だけ見た実装者が slice 1 に revalidate を入れないよう、**「slice 1 は意図的にライブ読み」を PR 本文・着手 issue に明示**する。

## 6. 既知のリスク（実装時に注意・現時点で決定不要）

- **Eventarc × Firestore サブコレクション**: `articles/{id}/events/{seq}` への collection-group 的トリガ指定が素直に効くか要検証。さらに**トリガは events doc 作成のみを拾い、`slugs/{slug}` 予約 doc では発火させない**設計にすること
- **Cloud Run → Cloud SQL 接続**: Cloud SQL Connector / Unix socket の配線（public IP 無効前提）
- **Firebase App Hosting の Actions 駆動**（設計 §9 の注意点）: Rollouts API / CLI のどちらで駆動するか、preview / production の扱い
