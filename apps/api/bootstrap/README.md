# apps/api/bootstrap

API の JVM エントリポイント。環境変数・Google Cloud client・infrastructure adapter・application service を配線し、zio-http server を起動する。

必須環境変数:

- `MORECAT_BEARER_TOKEN`: command endpoint で検証する Bearer token

任意環境変数:

- `PORT`: HTTP listen port（未指定時 `8080`。Cloud Run が設定する値を使用）

Firestore は Application Default Credentials と `FIRESTORE_EMULATOR_HOST` を含む Google Cloud SDK 標準設定を利用する。

## ローカル起動

リポジトリルートで開発環境に入る。

```sh
nix develop
```

同じ dev shell 内の2つのターミナルを使う。先にターミナル1で Firestore emulator を起動する。

```sh
# terminal 1: repository root
firebase emulators:start --only firestore --project demo-morecat
```

Firestore emulator が `127.0.0.1:8080` を使うため、ターミナル2では API を `8081` で起動する。

```sh
# terminal 2: repository root
cd apps/api
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
GOOGLE_CLOUD_PROJECT=demo-morecat \
PORT=8081 \
MORECAT_BEARER_TOKEN=local-dev-token \
sbt bootstrap/run
```

下書き作成 endpoint の疎通を確認する。

```sh
curl -i -X POST http://127.0.0.1:8081/articles \
  -H 'Authorization: Bearer local-dev-token' \
  -H 'Content-Type: application/json' \
  --data '{"slug":"hello-world","title":"Hello","body":"# Hello"}'
```

成功時は `201 Created` と UUIDv7 の `articleId` を返す。Firestore emulator のデータは <http://127.0.0.1:4000/firestore> で確認できる。終了時は両ターミナルで `Ctrl-C` を入力する。
