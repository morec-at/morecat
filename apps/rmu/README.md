# apps/rmu

Firestore の Article event を Eventarc から受信し、Postgres の read model を更新する Rust サービス。
API の Scala domain とはコード共有せず、event wire schema と projection fixture を契約として扱う。

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
