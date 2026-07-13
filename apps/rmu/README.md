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

