# morecat

## 開発環境

Nix で Node.js 24 と pnpm を用意します。Nix パッケージは `flake.lock`、Astro と Wrangler は `pnpm-lock.yaml` で固定しています。

```sh
nix develop path:.
pnpm install --frozen-lockfile
pnpm run dev
```

Flakes が無効な環境では `nix --extra-experimental-features 'nix-command flakes' develop path:.` を使います。

`pnpm run dev` は Astro のローカルサーバーで記事一覧を表示します。Cloudflare Workers のランタイムで確認するときは `pnpm run dev:workers` を使います。

OpenSpec の変更を検証するには、開発シェルで次を実行します。

```sh
pnpm dlx @fission-ai/openspec@2.2.0 validate deploy-public-blog-shell --strict
```

本番デプロイは、Cloudflare の認証後に `pnpm run deploy` で実行します。
