## Context

本番環境は一つとし、公開サイトから段階的に提供する。

## Goals / Non-Goals

* 公開サイトの HTTPS URL で空の記事一覧を表示する。
* 記事の取得と執筆機能は後続の変更で扱う。

## Decisions

Astro の Cloudflare アダプターで記事一覧をサーバー側で描画する。最初の公開には Workers が提供する URL を使う。

## Risks / Trade-offs

本番デプロイには対象 Cloudflare アカウントへの認証が必要になる。
