# Spec Delta

## Purpose

運営者本人が、PC のブラウザーで記事の下書きを書き、保存した内容を別の PC から開いて続きを書けるようにする。

## ADDED Requirements

### Requirement: 執筆機能は運営者本人に限定する

The system SHALL restrict access to the editor and all draft APIs to the configured operator account.

#### Scenario: 運営者がログインする

* **WHEN** 事前に指定した運営者がログインする
* **THEN** 下書きの一覧と編集機能を利用できる

#### Scenario: 未認証または別のアカウントでアクセスする

* **WHEN** 未認証の利用者、または指定外のアカウントで認証した利用者が執筆画面や下書き API にアクセスする
* **THEN** 下書きの内容を取得・変更できない

### Requirement: Markdown をプレビューできる

The editor SHALL preview Markdown with headings, lists, block quotes, links, and fenced code blocks, with raw HTML disabled.

#### Scenario: 本文を入力する

* **WHEN** 運営者が Markdown を入力する
* **THEN** 保存前にプレビューでき、生 HTML は実行されない

### Requirement: 下書きを保存して別の PC から再開できる

The editor SHALL save the draft on explicit request and allow the operator to reopen its latest saved title and body.

#### Scenario: 空欄のある下書きを保存する

* **WHEN** タイトルまたは本文が空の下書きを運営者が保存する
* **THEN** 下書きとして保存される

#### Scenario: 別の PC で再開する

* **WHEN** 運営者が別の PC でログインし、下書き一覧から保存済みの記事を開く
* **THEN** 最後に保存したタイトルと本文を編集できる

### Requirement: 古い画面からの上書きを防ぐ

The editor SHALL reject a save based on a stale revision and preserve the unsaved input on screen.

#### Scenario: 別の PC で先に保存された

* **WHEN** PC A が保存した後、以前の版を開いたままの PC B が保存を試みる
* **THEN** PC A の保存内容は維持され、PC B には競合が表示され、入力内容が画面に残る
