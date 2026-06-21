# apps/api/domain

API(JVM) の**純粋ドメイン**サブプロジェクト。IO 依存（Firestore / Postgres / tapir）を載せないことを sbt サブプロジェクト分割で強制する（ヘクサゴナルの内側）。

## 責務（書き込み側の語彙）
- Article のドメインイベント ADT（`ArticleDrafted` / `ArticlePublished`、`schemaVersion` 付き）
- 値オブジェクト（Iron + smart constructor）: `Slug` / `NonEmptyTitle` / `ArticleId`

**含めないもの**
- **読み取りモデル `Article` の projection fold** … RMU(Rust) の責務（イベントを fold して Cloud SQL に投影）。API は投影 fold をしない。
- **JSON 等の wire フォーマット / codec** … infrastructure 層の関心事。domain は `iron`(モデリング) のみに依存。
- **コマンド側の集約ロード（fold で現在状態を復元し不変条件を判定）** … タスク2で application/infrastructure と一緒に追加。読み取り投影とは別の「決定用集約状態」になりうる。

## RMU(Rust) との関係
v1 では **RMU を Rust** で実装するため、ドメインの**コードは共有しない**（cross-compile しない）。共有が要るのは **イベントの wire スキーマ** のみで、契約フィクスチャ（`fixtures/<case>/{events.json, expected-projection.json}`）で整合を担保する。投影 `fold` は RMU(Rust) に実装し、Scala 側はイベント encode が fixture と一致することを検証する。

## 設計上の約束
- 集約 ID はイベントストリームのパス（`articles/{id}/events/{seq}`）が持つため、イベント payload には含めない。
- タイムスタンプは **epoch millis(Long)** で表現（言語非依存。Rust の i64 とフィクスチャ共有でも素直）。

## テスト
```
cd apps/api && sbt domain/test
```
