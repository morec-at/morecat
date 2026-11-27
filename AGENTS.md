# AGENTS.md

## 開発環境

* リポジトリのルートで `nix develop path:.` を実行し、Node.js と pnpm のバージョンをそろえる。
* 依存関係のインストールには `pnpm install --frozen-lockfile` を使う。依存関係を変更した場合は `pnpm-lock.yaml` も更新する。
* 通常のローカル開発は `pnpm run dev`、Cloudflare Workers のランタイムでの確認は `pnpm run dev:workers` を使う。
* 公開サイトの変更は `pnpm run build` で検証する。環境の詳細は [README.md](README.md) を参照する。

## 仕様と設計判断

* 利用者に見える振る舞いは [OpenSpec の仕様](openspec/specs/) に記録する。変更は本番で確認できる小さな単位に分け、提案、実装、本番確認、アーカイブの順で進める。
* OpenSpec の変更は `pnpm dlx @fission-ai/openspec@2.2.0 validate <change-name> --strict` で検証する。完了条件を確認してから仕様を反映し、変更をアーカイブする。
* 用語は [GLOSSARY.md](GLOSSARY.md) に合わせる。設計判断と理由は [docs/adr/](docs/adr/) に [MADR 4.0.0 Full Template](https://adr.github.io/madr/#full-template) で記録する。

## 本番デプロイ

* 公開サイトは Cloudflare Workers の本番環境に段階的にデプロイする。デプロイが依頼されたときは、認証済みの状態で `pnpm run deploy` を実行する。
* デプロイ後は本番の HTTPS URL で、HTTP ステータスと変更の完了条件に該当する表示や操作を確認する。
* 認証情報、ローカルの IDE 設定、ビルド生成物はコミット対象から外す。
