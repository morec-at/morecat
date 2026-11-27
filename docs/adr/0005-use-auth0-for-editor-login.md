---
status: accepted
---

# 執筆画面のログインに Auth0 を使う

## Context and Problem Statement

運営者は複数の PC から執筆する。執筆画面と下書き API は運営者本人だけが使えるようにする。

## Decision Drivers

* 既存のアカウントでログインできること。
* Auth0 の設定と運用を実際に試せること。

## Considered Options

* Auth0
* CMS 独自の認証

## Decision Outcome

Auth0 を採用する。認証サービスを自分のアプリに組み込んで運用する経験を得られ、パスワードの管理を認証サービスに委ねられる。

### Consequences

API は事前に設定した Auth0 のユーザー ID を照合する。アカウントの紐付けと本番のログイン設定は Auth0 に依存する。

### Confirmation

指定した運営者が下書きを読み書きでき、未認証と指定外の利用者が拒否されることを確認する。

## Pros and Cons of the Options

### Auth0

* 長所。既存アカウントでログインでき、認証の運用を委ねられる。
* 短所。外部サービスの設定とユーザー ID の紐付けを管理する必要がある。

### CMS 独自の認証

* 長所。認証の仕様を CMS で制御できる。
* 短所。パスワードの管理や再発行を CMS が担う。

## More Information

[用語集](../../GLOSSARY.md) に運営者の定義を記載している。
