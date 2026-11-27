---
status: accepted
---

# Firestore のイベントでリードモデルを更新する

## Context and Problem Statement

記事履歴からの再構築を維持し、イベントストアの保守と Updater の常時稼働を減らしたい。

## Decision Drivers

* イベント履歴から再構築でき、Updater がイベント到着時に起動できること。

## Considered Options

* KurrentDB の購読機能を使う。
* Firestore のイベントを Eventarc で Cloud Run に届ける。

## Decision Outcome

記事の履歴を Firestore に追記し、ドキュメント作成を Eventarc で Cloud Run の Read Model Updater に届けることで、イベントストアと購読処理の常時運用を減らす。通知が重複したり前後したりしても、Updater は記事ごとの履歴を連番順に処理し、PostgreSQL のリードモデルと最終反映連番を同じトランザクションで更新する。公開と公開取り消しの完了は対象イベントが公開用リードモデルに反映されてから表示し、リードモデルは履歴から再構築できるようにする。

### Consequences

ZIO API は、編集開始時の連番との照合、初回公開時のスラッグ確保、イベント追記を Firestore の一つのトランザクションで行う。連番またはスラッグが競合した場合は操作を拒否し、入力内容は編集画面に残す。

### Confirmation

通知を重複・逆順で渡し、履歴順に反映されることと、対象イベントが反映されてから完了が表示されることを確認する。

## Pros and Cons of the Options

### KurrentDB の購読機能を使う。

* 長所。イベントストアの組み込み購読を利用できる。
* 短所。イベントストアが常時稼働する。

### Firestore のイベントを Eventarc で Cloud Run に届ける。

* 長所。イベントストアの保守を委ね、Updater をイベント到着時に起動できる。
* 短所。通知の重複や順序の逆転を Updater で扱う必要がある。

## More Information

この決定は [ADR 0002](0002-event-sourcing-with-kurrentdb.md) を置き換える。
