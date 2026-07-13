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

```sh
cd ../..
firebase emulators:exec \
  --config apps/api/firebase.json \
  --only firestore \
  --project demo-morecat \
  'cargo llvm-cov --manifest-path apps/rmu/Cargo.toml --locked --all-features --fail-under-lines 100 --fail-under-regions 100'
```

`--all-features` では Firestore SDK の統合テストも動くため、Firestore emulator が必要になる。`firebase emulators:exec` はテスト中だけ emulator を起動し、終了後に停止する。

HTML レポートを確認する場合は、上記の内側のコマンドに `--html --open` を追加する。Rust stable では LLVM branch coverage が未安定のため、式や分岐アーム単位で計測する region coverage を使用する。
