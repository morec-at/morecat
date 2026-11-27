---
status: superseded by ADR-0003
---

# 記事の履歴を KurrentDB に保存する

## Context and Problem Statement

記事の履歴から現在の状態を復元し、購読したイベントでリードモデルを更新したい。

## Decision Drivers

* 記事の履歴を再生でき、イベントストアから変更を購読できること。

## Considered Options

* PostgreSQL に履歴を保存し、Updater が未処理のイベントを読み取る。
* KurrentDB の組み込み購読機能を使う。

## Decision Outcome

記事の作成から削除までのイベントを KurrentDB に記録し、履歴から下書きと公開版を復元できるようにする。Read Model Updater は catch-up subscription でイベントを順に受け取り、PostgreSQL のリードモデルと購読の処理位置を同じトランザクションで更新する。編集画面は、公開または公開取り消しのイベントが記録されたら「反映中」と表示し、その操作のイベントが公開用リードモデルに反映されたことを確認してから完了を表示する。

### Consequences

* 購読機能を使って未処理のイベントから再開できる。
* KurrentDB と PostgreSQL の稼働環境が必要になる。

### Confirmation

当初の検証条件は、Updater の再起動後に購読を再開でき、公開完了が反映後に表示されることだった。

## Pros and Cons of the Options

### PostgreSQL に履歴を保存し、Updater が未処理のイベントを読み取る。

* 長所。イベント履歴とリードモデルに同じデータベース製品を使える。
* 短所。イベントを拾う仕組みをアプリ側で用意する必要がある。

### KurrentDB の組み込み購読機能を使う。

* 長所。購読機能を使って未処理のイベントから再開できる。
* 短所。KurrentDB と PostgreSQL の稼働環境が必要になる。

## More Information

[ADR 0003](0003-event-sourcing-with-firestore.md) がこの決定を置き換えた。
