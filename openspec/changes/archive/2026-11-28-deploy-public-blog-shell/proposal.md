## Why

初版の最初の段階で本番 URL を確保し、以降の変更を小さくデプロイできるようにする。

## What Changes

* Astro で公開記事一覧の空の状態を表示する。
* Cloudflare Workers に本番デプロイできるようにする。

## Capabilities

### New Capabilities

* `public-blog` — 読者が公開記事一覧を閲覧する。

### Modified Capabilities

なし。

## Impact

公開サイトのルートと Cloudflare Workers のデプロイ設定を追加する。
