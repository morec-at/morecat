# apps/rmu

Firestore の Article event を Eventarc から受信し、Postgres の read model を更新する Rust サービス。
API の Scala domain とはコード共有せず、event wire schema と projection fixture を契約として扱う。

## Runtime configuration

必須環境変数:

- `GOOGLE_CLOUD_PROJECT`: event store を読む Firestore project ID
- `DATABASE_URL`: Postgres 接続 URL（例: `postgres://morecat:password@127.0.0.1:5432/morecat`）

任意環境変数:

- `PORT`: HTTP listen port（未指定時 `8080`。Cloud Run が設定する値を使用）

Firestore は Application Default Credentials と `FIRESTORE_EMULATOR_HOST` を含む Google Cloud SDK 標準設定を利用する。`SIGINT` または `SIGTERM` を受けると処理中の HTTP request を待ってから終了する。

## Local startup

Postgres に次の migration を順番に適用しておく。

- `apps/api/infrastructure/src/main/resources/db/migration/V1__create_articles.sql`
- `apps/api/infrastructure/src/main/resources/db/migration/V2__create_rmu_dead_letters.sql`

リポジトリルートで `nix develop .#rust` に入り、2つのターミナルを使う。ターミナル1で Firestore emulator を起動する。

```sh
# terminal 1: repository root
firebase emulators:start \
  --config apps/api/firebase.json \
  --only firestore \
  --project demo-morecat
```

Firestore emulator が `127.0.0.1:8080` を使うため、ターミナル2では RMU を `8081` で起動する。

```sh
# terminal 2: repository root
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
GOOGLE_CLOUD_PROJECT=demo-morecat \
DATABASE_URL=postgres://morecat:local-dev-password@127.0.0.1:5432/morecat \
PORT=8081 \
cargo run --manifest-path apps/rmu/Cargo.toml
```

終了時は両ターミナルで `Ctrl-C` を入力する。

## Manual replay

`replay` command は Firestore の全 `events` subcollection を collection-group query で列挙し、article ID ごとに全 event stream を再読込して Postgres projection を更新する。HTTP listener は起動せず、最初の読み取り・projection エラーで非ゼロ終了する。

```sh
# repository root
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
GOOGLE_CLOUD_PROJECT=demo-morecat \
DATABASE_URL=postgres://morecat:local-dev-password@127.0.0.1:5432/morecat \
cargo run --manifest-path apps/rmu/Cargo.toml -- replay
```

成功時は `Replayed N article(s)` を出力する。本番では同じ container image の command を `replay` に上書きした Cloud Run Job として実行できる。

通常の再送・追いつき用途では `last_applied_seq` が進む projection だけを更新するため、繰り返し実行できる。完全再構築では migration 適用済みの空の Postgres database を対象にする。command 自体は `articles` table の削除や `TRUNCATE` を行わないため、既存 database を初期化する場合は backup と切り戻し手順を別途用意してから実施する。

## Development

リポジトリルートで Rust 用 dev shell に入り、テストを実行する。

```sh
nix develop .#rust
cd apps/rmu
cargo test
```

API と同様に line / region coverage はともに 100% を必須とする。CI と同じ条件で確認するには次を実行する。
Docker を起動し、macOS で標準以外の Docker context を使う場合は Testcontainers に socket を伝えてから実行する。

```sh
cd ../..
export DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
firebase emulators:exec \
  --config apps/api/firebase.json \
  --only firestore \
  --project demo-morecat \
  'cargo llvm-cov --manifest-path apps/rmu/Cargo.toml --locked --all-features --fail-under-lines 100 --fail-under-regions 100'
```

`--all-features` では Firestore SDK と Postgres adapter の統合テストも動くため、Firestore emulator と Docker が必要になる。Postgres 17 の Testcontainers はテスト中だけ起動し、終了後に停止する。`firebase emulators:exec` も同様に Firestore emulator の起動と停止を管理する。

HTML レポートを確認する場合は、上記の内側のコマンドに `--html --open` を追加する。Rust stable では LLVM branch coverage が未安定のため、式や分岐アーム単位で計測する region coverage を使用する。
