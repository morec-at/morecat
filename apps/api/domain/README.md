# apps/api/domain

API(JVM) の**純粋ドメイン**サブプロジェクト。IO 依存（Firestore / Postgres / tapir）を載せないことを sbt サブプロジェクト分割で強制する（ヘクサゴナルの内側）。

## 責務
- Article のドメインイベント ADT（`ArticleDrafted` / `ArticlePublished`、`schemaVersion` 付き）
- 値オブジェクト（Iron + smart constructor）: `Slug` / `NonEmptyTitle` / `ArticleId`
- 読み取りモデル `Article` と **projection fold（純粋関数）**

**JSON 等の wire フォーマットは持たない**（serialization は infrastructure 層の関心事）。domain は `iron`(モデリング) のみに依存し、`zio-json` 等の codec は infrastructure に置く。

## RMU(Rust) との関係
v1 では **RMU を Rust** で実装するため、ドメインの**コードは共有しない**（cross-compile しない）。共有が要るのは **イベントの wire スキーマ** のみで、契約フィクスチャ（`fixtures/<case>/{events.json, expected-projection.json}`）を Scala 側・Rust 側双方のテストで検証して整合を担保する。`fold` の振る舞いは RMU(Rust) 側に再実装し、drift はフィクスチャで検出する。Scala の `fold` は契約の参照実装として残す。

## 設計上の約束
- 集約 ID はイベントストリームのパス（`articles/{id}/events/{seq}`）が持つため、イベント payload には含めない。`fold(id, events)` に id を外から渡す。
- タイムスタンプは **epoch millis(Long)** で表現（言語非依存。Rust の i64 とフィクスチャ共有でも素直）。
- `fold` は順序付きの完全ストリームを畳み込む前提（RMU は全ストリームを再読込して fold する）。

## テスト
```
cd apps/api && sbt domain/test
```
