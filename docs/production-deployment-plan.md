# スライス1・マイルストーン B 実装計画

`slice-1-plan.md` タスク6を実装単位に分割する。本番で下書き作成 → 公開 → Firestore → Eventarc → RMU → Cloud SQL → API → viewer の貫通を確認することが完了条件。

## 現状

- マイルストーン A（ローカル E2E）は実装済み。API / RMU / viewer と E2E の CI がある。
- `infra/` に HCP Terraform workspace の作成・設定と mock テストを実装済み。`emag-org / morecat-prod` の作成と同一 state の HCP Terraform への移行を実施済み（管理対象5件）。remote plan は Terraform 1.17.1 で `No changes` を確認済み。GCP リソース、API / RMU のコンテナ定義、デプロイ workflow は未実施。
- API は JDBC URL・DB ユーザー・DB パスワード、RMU は Postgres URL を環境変数から受け取る。非公開 Cloud SQL 接続は実装時に両言語で検証する。
- viewer は `MORECAT_API_BASE_URL` から公開 API に接続する。スライス1は毎回ライブ読み。

## 着手前の確認

- [ ] 本番 project ID、課金の有効化、既存リソースの有無
- [ ] リージョンと Firestore のロケーション（既存 DB がある場合はその設定）
- [x] Terraform state と plan / apply は HCP Terraform（org は作成済み）
- [x] HCP Terraform organization 名: `emag-org`
- [x] workspace `morecat-prod` の作成と state 移行（Terraform `1.17.1`、自動 apply 無効、working directory `infra`）
- [x] remote runner 用 `TFE_TOKEN` の登録と remote plan の確認（[run-mr8qEBPpcc8YJY1u](https://app.terraform.io/app/emag-org/morecat-prod/runs/run-mr8qEBPpcc8YJY1u)、`No changes`）
- [ ] GitHub VCS 接続
- [x] workspace と GCP を直下 `infra/` の単一構成・単一 state で管理。初回 local state を HCP Terraform へ移行する
- [ ] GCP WIF の初期設定主体
- [ ] GitHub の本番 environment と承認者、WIF の対象 repository / branch
- [ ] Firebase App Hosting と GitHub 連携の既存設定
- [ ] Cloud SQL の構成・費用・削除保護・バックアップ方針

値が確認できるまでは仮の project ID で apply しない。既存リソースがある場合は import 方針を決める。

## HCP Terraform の運用方針

- HCP Terraform が state とインフラの plan / apply を担当。GitHub Actions はテスト、image build、アプリ deploy を担当する。
- 初回はローカルで workspace を作成し、同じ state を HCP Terraform へ移行する。移行後に VCS 接続を追加し、working directory は `infra`。本番 branch は実装時に確認する。PR で speculative plan、マージ後は HCP Terraform で承認して apply する（auto-apply 無効）。
- インフラ apply の承認は HCP Terraform、アプリ deploy の承認は GitHub protected environment で管理する。両者は別の承認境界となる。
- HCP Terraform → GCP は Dynamic Provider Credentials（OIDC / WIF）。plan / apply 用 SA を分離し、信頼条件は organization / workspace / run phase に限定する。GitHub Actions の WIF とは分離する。
- 初回の WIF / IAM は管理者の認証でローカル実行し、その後 Dynamic Provider Credentials に切り替える。長期サービスアカウントキーは登録しない。別の bootstrap state は作らない。
- HCP Terraform の state にも secret 値を記録しない。DB ユーザー作成と migration の実行経路は、非公開 Cloud SQL への到達性を含めて検証する。

公式資料: [VCS-driven workflow](https://developer.hashicorp.com/terraform/cloud-docs/run/ui)、[GCP Dynamic Provider Credentials](https://developer.hashicorp.com/terraform/cloud-docs/dynamic-provider-credentials/gcp-configuration)。

## 実装順序と完了条件

### B1. Terraform の土台と実行環境

Terraform の配置はリポジトリ直下の `infra/`。workspace と GCP を単一 root module・単一 state で管理する。操作手順は `infra/README.md` に記載。

- 開発用 CLI を nix の専用 shell に追加し、Terraform のバージョンと公式バイナリの SHA256 を `flake.nix` で固定。
- provider と lock file、入力変数、HCP Terraform workspace の設定・接続手順を用意。
- 既存 org 内の workspace と GCP WIF を bootstrap し、PR plan と承認付き apply の権限を設定。
- state / plan / 秘匿入力の Git 除外を設定。
- 最初の対象は必要 API、Artifact Registry、用途ごとのサービスアカウント。アプリ用権限は必要となる段階で追加する。
- 完了条件: fmt / validate が成功し、対象プロジェクト向け plan をレビューできる。まだ本番 apply は行わない。

### B2. 配布用コンテナ

API / RMU のコンテナと、ビルド・起動の確認手順を用意。

- API は sbt の配布方法を決め、JDK と実行時依存を含める。
- RMU は HTTP 起動と `replay` を同一 image で実行できるようにする。
- 記事データや認証情報を image に含めない。
- 完了条件: 各 image が `PORT` で起動し、ローカル依存サービスを利用する疎通が成功。

### B3. データ基盤と接続

Firestore、非公開 Cloud SQL、Secret Manager とネットワークを宣言。

- Cloud SQL の非公開接続方式を選び、Scala JDBC / Rust sqlx の双方で接続を検証する。必要なら先にアプリ設定を変更する。
- migration / API 読取 / RMU 投影用の DB ユーザーを分離し、SQL 権限を定義。
- migration の実行主体・実行方法と失敗時の復旧手順を用意。
- Terraform は secret の器と権限を管理。パスワードと Bearer の値は state / GitHub Secrets に保持しない方式を決める。
- 完了条件: migration 後、API は読取、RMU は投影更新が可能。権限外の操作は拒否される。

### B4. Cloud Run と Eventarc

API / RMU、Eventarc trigger、replay 用 Job を宣言。

- API の公開クエリと Bearer 必須コマンドを維持。
- RMU の invoke 権限は Eventarc 用 SA に限定。
- Firestore trigger は `articles/{id}/events/{seq}` の作成を対象とし、slug 予約では発火しないことを確認。
- 再送・DLQ の設定可能範囲を公式仕様で確認し、恒久エラーの記録と手動 replay を含めて運用を文書化。
- 完了条件: 実イベントで投影が更新され、未認証 RMU 呼出しは拒否。再送しても重複適用されない。

### B5. viewer と本番 CI/CD

Firebase App Hosting の設定と GitHub Actions のデプロイを用意。

- App Hosting の Actions 駆動方式を公式仕様で確認し、production / preview の扱いを明記。
- WIF で認証し、静的サービスアカウントキーを使わない。
- インフラ plan / apply は HCP Terraform で権限分離・承認する。アプリ deploy は GitHub protected environment の承認を経る。
- CI の既存 required checks を維持。Actions は SHA 固定。
- 完了条件: CI と本番承認を経て配布でき、viewer が本番 API の公開記事を SSR 表示する。

### B6. 本番受入れと運用

- 認証なしのコマンド拒否、draft の非公開、slug 競合、publish の冪等性を確認。
- 下書き作成 → 公開 → viewer の貫通を確認。
- Secret の生成・ローテーション・失効、migration、replay、障害確認、切り戻し手順を記載。
- 本番データを残す削除保護とバックアップを確認し、実装計画のマイルストーン B を更新。

## 進め方

各単位で仕様のテストリストを用意し、動作変更は Red → Green → Refactor で進める。外部仕様は実装時に公式ドキュメントで確認する。リソース作成を伴う apply や本番 deploy は、plan と変更対象がレビューできる状態になってから実施する。
