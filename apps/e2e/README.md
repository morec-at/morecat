# local end-to-end

スライス1のローカル walking skeleton を、公開 HTTP 境界から一気通貫で検証する。

`local-e2e.sh` は一時 Postgres container、Firestore emulator、API、RMU の CDC bridge、viewer を起動し、次の仕様を確認する。

- draft 作成直後の公開 GET は `404`
- Bearer token が未指定または不正な command は `401`
- 予約済み slug による draft 作成は `409`
- 未作成 article の publish は `404`、期待 version 不一致は `409`
- viewer は不在または draft の記事を `404` にする
- publish 後は CDC bridge が Postgres projection を更新し、公開 GET は `200`
- viewer の `/posts/{slug}` は記事を SSR し、危険な Markdown を除去する

## 実行

Docker daemon を起動し、リポジトリルートの dev shell で実行する。初回は Scala、Rust、Node の依存解決とビルドに時間がかかる。

```sh
nix develop
./apps/e2e/local-e2e.sh
```

`apps/ui` の依存は事前に `pnpm install --frozen-lockfile` で導入しておく。既定で Firestore emulator は `8080`、API は `18081`、viewer は `13000` を使う。API と viewer の port はそれぞれ `MORECAT_E2E_API_PORT` と `MORECAT_E2E_VIEWER_PORT` で変更できる。

テスト終了時は成功・失敗を問わず、スクリプト自身が起動したプロセスと一時 Postgres container を停止する。失敗時は各プロセスの末尾ログを表示する。

## CI

`.github/workflows/e2e.yml` は全 pull request と、関連 path が変わった `main` push で同じスクリプトを実行する。CI では `MORECAT_E2E_ARTIFACT_DIR=.artifacts/local-e2e` を指定し、失敗時の API・RMU bridge・Firestore・viewer・Postgres log を14日間 artifact として保存する。環境変数を指定しないローカル実行ではログを永続化しない。
