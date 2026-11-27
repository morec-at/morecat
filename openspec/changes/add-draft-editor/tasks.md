# Tasks

## 1. 認証と下書き API

- [ ] 1.1 ZIO HTTP と Auth0 を接続し、運営者、指定外アカウント、未認証の各ケースで API の許可・拒否を確認する。
- [ ] 1.2 Firestore へのイベント追記、下書き一覧と取得を実装し、空欄の保存、履歴からの復元、古い連番での保存拒否を検証する。

## 2. 執筆画面

- [ ] 2.1 Scala.js と Laminar で下書き一覧、編集、Markdown プレビューを実装し、対象の構文と生 HTML の扱いを確認する。
- [ ] 2.2 保存と競合時の表示を実装し、保存を拒否されても入力したタイトルと本文が残ることを確認する。

## 3. 本番確認

- [ ] 3.1 Auth0、執筆用 SPA、Cloud Run API、Firestore を本番に設定し、Firestore の日次バックアップが 7 日間保持される設定を確認する。
- [ ] 3.2 本番で本人限定のログイン、別の PC からの再開、古い画面からの保存拒否と入力保持を確認する。
*** Add File: docs/adr/0005-use-auth0-for-editor-login.md
---
status: accepted
---

# 執筆画面のログインに Auth0 を使う

## Context and Problem Statement

複数の PC から執筆するため、運営者本人を識別して編集権限を確認する必要がある。

## Decision Drivers

* 既存アカウントでのログインと、運営者一人へのアクセス制限。

## Considered Options

* Auth0
* CMS 独自の認証

## Decision Outcome

Auth0 を採用する。認証サービスを実際に使って設定と運用を経験したいことに加え、パスワードの管理を認証サービスに委ねられる。

### Consequences

設定した Auth0 のユーザー ID を API で照合する。アカウントの紐付けとログイン設定は Auth0 に依存する。

### Confirmation

指定した運営者だけが下書きを読み書きでき、未認証または指定外の利用者が拒否されることを確認する。

## Pros and Cons of the Options

### Auth0

* 長所。認証の運用を委ねられる。
* 短所。外部サービスの設定とアカウントの紐付けを管理する。

### CMS 独自の認証

* 長所。認証の仕様を CMS で制御できる。
* 短所。パスワード管理や再発行を CMS 側で担う。

## More Information

[執筆画面と下書き保存の設計](../../openspec/changes/add-draft-editor/design.md) を参照。
