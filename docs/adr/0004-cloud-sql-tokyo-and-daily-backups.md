---
status: accepted
---

# 東京の Cloud SQL と日次バックアップを採用する

## Context and Problem Statement

両ストアの日次バックアップを運用し、ブログ全体を月額約 5,000 円に収めたい。

## Decision Drivers

* 月額約 5,000 円、日次バックアップの 7 日間保持、保守の手軽さ。

## Considered Options

* Render または Neon を使い、日次バックアップを別途設定する。
* Cloud SQL 東京の最小構成と標準の日次バックアップを使う。

## Decision Outcome

Firestore から生成する PostgreSQL のリードモデルは、東京の Cloud SQL for PostgreSQL Enterprise に置く。初版は単一ゾーンの `db-f1-micro` と SSD 10 GiB で始める。Cloud Run と同じリージョンに配置でき、Cloud SQL の標準機能で日次バックアップを運用できるため、別のリージョンに PostgreSQL を置いてバックアップジョブと保存先を管理する構成より運用の手間が少ない。共有 CPU の `db-f1-micro` は Cloud SQL の SLA 対象外となる。

Firestore と Cloud SQL の両方で日次自動バックアップを有効にし、7 日間保持する。Firestore のバックアップは誤操作や障害で失われた記事の履歴を復元するために使う。Cloud SQL のバックアップはリードモデルと最終反映連番をまとめて戻し、復旧時間を短縮するために使う。7 日間の保持で、誤操作に数日後に気づいた場合も、発生前の日次バックアップを選べる。Firestore を復元した場合は、復元後の履歴からリードモデルと最終反映連番を再構築する。

### Consequences

* Cloud Run と同じリージョンに置き、標準のバックアップ機能を使える。
* 最小の共有 CPU プランは SLA 対象外で、インスタンスの常時稼働費がかかる。

### Confirmation

両ストアの日次バックアップと 7 日間の保持設定を確認し、Firestore の復元からリードモデルの再構築までを検証する。稼働後の請求を月額 5,000 円の目標と照合する。

## Pros and Cons of the Options

### Render または Neon を使い、日次バックアップを別途設定する。

* 長所。Render は小規模構成の料金を見通しやすく、Neon は利用がない間に停止できる。
* 短所。日次バックアップの実行と保存先を別途管理する必要がある。

### Cloud SQL 東京の最小構成と標準の日次バックアップを使う。

* 長所。Cloud Run と同じリージョンに置き、標準のバックアップ機能を使える。
* 短所。最小の共有 CPU プランは SLA 対象外で、インスタンスの常時稼働費がかかる。

## More Information

### 月額の見積もり

| 項目 | 月額（米ドル） | 前提 |
| --- | ---: | --- |
| Cloud SQL のインスタンス | $10.22 | 東京の参考単価 $0.014/時 × 730 時間 |
| Cloud SQL の SSD とバックアップ | $4.00 | SSD 10 GiB と日次バックアップ 7 日分の予算枠 |
| Firestore とバックアップ | $1.00 | 履歴は 1 GiB 以下で、通常の読み書きは無料枠内。$1 は日次バックアップ 7 日分の予算枠 |
| Cloud Run、Eventarc、Auth0 | $0 | 少量利用を想定。ZIO API と Updater は最小インスタンス 0 |
| Cloudflare Workers | $0〜$5.00 | Astro の配信。無料枠または有料プランの基本料金 |
| 合計 | **$15.22〜$20.22** | **約 2,420〜3,220 円**（1 米ドル約 159 円） |

有料 Workers を使う場合でも、目標の月額 5,000 円まで約 1,780 円の余裕がある。税、ドメイン代、復元時の費用は別途。ストレージとバックアップの金額は予算枠であり、実際の請求額は東京の単価と保存量に応じて変わる。通信やコンテナイメージの保存が無料枠を超えた場合は追加費用がかかる。

[Cloud SQL の料金](https://cloud.google.com/sql/pricing) · [東京のインスタンス参考単価](https://instances.usage.ai/gcp/sql/region/asia-northeast1) · [Cloud SQL のバックアップ](https://docs.cloud.google.com/sql/docs/postgres/backup-recovery/backup-options) · [Firestore の料金](https://cloud.google.com/firestore/pricing) · [Firestore の日次バックアップ](https://docs.cloud.google.com/firestore/native/docs/backups) · [Cloudflare Workers の料金](https://developers.cloudflare.com/workers/platform/pricing/)
