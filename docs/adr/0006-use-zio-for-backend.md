---
status: accepted
---

# Scala バックエンドに ZIO を使う

## Context and Problem Statement

編集 API と Read Model Updater を Scala で実装する。

## Decision Drivers

* Scala で Web API とイベント処理を実装・運用する経験を得ること。

## Considered Options

* ZIO と ZIO HTTP
* Play Framework

## Decision Outcome

バックエンドには ZIO を採用し、HTTP の入口には ZIO HTTP を使う。この CMS で Scala の Web API とイベント処理を ZIO で実装・運用してみたいことが選定理由となる。

### Consequences

API と Updater で ZIO を使う。実装とテストは ZIO のエフェクトおよび依存関係の組み立て方に沿う。

### Confirmation

編集 API が Cloud Run 上で動作することを確認する。後続の変更では、イベントを受信した Updater が PostgreSQL に反映することを確認する。

## Pros and Cons of the Options

### ZIO と ZIO HTTP

* 長所。API と Updater を実装しながら ZIO を学べる。
* 短所。ZIO の設計に習熟する時間がかかる。

### Play Framework

* 長所。Scala で HTTP アプリケーションを構成できる。
* 短所。API 全体を ZIO で組み立てる経験を得るという学習目的への寄与は小さい。

## More Information

[ADR 0003](0003-event-sourcing-with-firestore.md) に Updater とイベントストアの構成を記載している。
