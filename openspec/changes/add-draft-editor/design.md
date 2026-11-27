# Design

## Context

読者向け Astro サイトは Cloudflare Workers で公開済み。執筆機能は独立した SPA と API として本番に追加する。

## Goals / Non-Goals

運営者が PC のブラウザーで下書きをプレビュー、保存し、別の PC で再開できるようにする。公開・公開取り消し・削除・画像アップロードは後続の変更で扱う。

## Decisions

* 執筆用 SPA は Scala.js と Laminar で作る。Scala.js でリアクティブな UI を試したいことが選定理由で、Markdown 入力とプレビューの連動に使う。
* Auth0 で認証し、事前に指定したユーザー ID にだけ執筆機能を許可する。すべての編集 API で認証と権限を確認し、ログインの入口に `noindex` を付ける。[ADR 0005](../../../docs/adr/0005-use-auth0-for-editor-login.md)
* Scala と ZIO HTTP で API を実装し、Cloud Run の最小インスタンス数は 0 にする。[ADR 0006](../../../docs/adr/0006-use-zio-for-backend.md)
* Firestore に記事の作成・下書き保存のイベントを追記し、履歴から下書きを復元する。保存時は編集開始時点の連番と現在の連番を同じトランザクションで照合してから追記する。[ADR 0003](../../../docs/adr/0003-event-sourcing-with-firestore.md)
* Markdown は見出し、リスト、引用、リンク、コードブロックを扱い、生 HTML は無効にする。描画ルールは将来の公開ページでも再利用できる形にする。
* 記事データを保存する段階で Firestore の日次バックアップを有効にし、7 日間保持する。[ADR 0004](../../../docs/adr/0004-cloud-sql-tokyo-and-daily-backups.md)

## Risks / Trade-offs

保存が競合した場合はサーバー側の内容を保持し、入力中の内容は画面に残す。最新版との統合は運営者が行う。Cloud Run の初回リクエストでは起動待ちが発生し得る。
