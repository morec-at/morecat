# public-blog Specification

## Purpose

読者がログインせずに、公開サイトで個人ブログの公開記事一覧を閲覧できるようにする。記事がまだ公開されていない場合は、その状態を表示する。

## Requirements

### Requirement: 公開記事一覧を閲覧できる

The public blog SHALL serve its article index over HTTPS without requiring sign-in.

#### Scenario: 公開記事がまだ存在しない

* **WHEN** 読者が公開サイトのトップページを開く
* **THEN** サイトは HTTP 200 を返し、記事一覧の見出しと公開記事がまだない旨を表示する
