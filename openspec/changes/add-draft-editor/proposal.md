# Proposal

## Why

本番で運営者本人が記事を書き始め、別の PC から続きを書けるようにする。

## What Changes

* 運営者本人向けに、下書きの一覧、作成、再編集と Markdown プレビューを提供する。
* 下書きをサーバーに保存し、古い画面からの上書き時には入力内容を保持して競合を知らせる。
* 公開、公開取り消し、記事の削除、画像アップロードは後続の変更で扱う。

## Capabilities

### New Capabilities

* `draft-editor` — 運営者本人が下書きを執筆、保存、再開する。

### Modified Capabilities

## Impact

執筆用 SPA、Auth0、Cloud Run の API、Firestore を追加する。
