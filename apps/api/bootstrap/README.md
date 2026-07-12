# apps/api/bootstrap

API の JVM エントリポイント。環境変数・Google Cloud client・infrastructure adapter・application service を配線し、zio-http server を起動する。

必須環境変数:

- `MORECAT_BEARER_TOKEN`: command endpoint で検証する Bearer token

Firestore は Application Default Credentials と `FIRESTORE_EMULATOR_HOST` を含む Google Cloud SDK 標準設定を利用する。
