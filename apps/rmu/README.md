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
cargo llvm-cov --locked --all-features --fail-under-lines 100 --fail-under-regions 100
```

HTML レポートを確認する場合は `cargo llvm-cov --html --open` を実行する。Rust stable では LLVM branch coverage が未安定のため、式や分岐アーム単位で計測する region coverage を使用する。
