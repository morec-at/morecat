# morecat infrastructure

HCP Terraform workspace と GCP リソースを、このディレクトリの単一 root module・単一 state で管理する。現在は workspace の作成と設定までを実装。GCP リソース、WIF、VCS 接続は後続で追加する。

## 検証する仕様

- 本番 apply は承認必須。
- remote 実行の working directory はリポジトリ直下の `infra/`。
- destroy plan と強制削除を禁止し、他 workspace へ state を共有しない。
- remote run に必要な非秘密の入力値を workspace に設定する。

`tests/workspace.tftest.hcl` は provider を mock し、外部 API を呼ばずに plan を検証する。workspace resource の `prevent_destroy` は削除を防ぐ。ただし resource 自体を構成から削除するとこの保護も消えるため、削除変更はレビューする。

```sh
nix develop .#infra
terraform -chdir=infra init -backend=false
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra validate
terraform -chdir=infra test
```

Terraform CLI は `flake.nix` で公式バイナリのバージョンと SHA256 を固定する（現在 `1.17.1`）。workspace の `terraform_version` も同じバージョンを指定する。provider の lock file もコミットする。

## 初回: ローカル state で workspace を作成

organization は `emag-org`（既存）、workspace 名は `morecat-prod` を既定とする。workspace を作成・管理できる HCP Terraform token が必要。既存 workspace を利用する場合は先に import し、同名 workspace を再作成しない。

```sh
cp infra/terraform.tfvars.example infra/terraform.tfvars
# 必要なら terraform.tfvars の organization と workspace 名を変更する。
terraform login
terraform -chdir=infra init
terraform -chdir=infra plan -out=workspace.tfplan
terraform -chdir=infra apply workspace.tfplan
```

初回は `cloud.tf` が存在しないことを確認する。plan には workspace / settings と非秘密の workspace 変数だけが含まれる。token は CLI の認証ファイルまたは `TFE_TOKEN` から provider に渡し、tfvars や provider の `token` 引数に書かない。認証ファイルの内容をログに出さない。

### NixOS で login が失敗する場合

公式バイナリの秘密入力ライブラリは `/bin/stty` を直接実行するため、そのパスがない NixOS では `failed turning off console echo ... no such file or directory` になる。PATH に `stty` を追加しても解消しない。

[token 発行画面](https://app.terraform.io/app/settings/tokens)で user token を生成し、エディタで `~/.terraform.d/credentials.tfrc.json` に次の形式で保存する。既存ファイルがある場合は他の認証情報を残して項目を追加する。

```json
{
  "credentials": {
    "app.terraform.io": {
      "token": "REPLACE_WITH_YOUR_TOKEN"
    }
  }
}
```

ディレクトリは `mkdir -p ~/.terraform.d` で作成し、保存後は `chmod 700 ~/.terraform.d` と `chmod 600 ~/.terraform.d/credentials.tfrc.json` を実行する。token はエディタへ直接貼り付け、チャットやシェルコマンドに書かない。設定後は `terraform login` を再実行せず、初回手順の `terraform -chdir=infra init` と `terraform -chdir=infra plan -out=workspace.tfplan` へ進める。

参考: [Terraform の依存バージョン](https://github.com/hashicorp/terraform/blob/v1.17.1/go.mod)、[秘密入力の実装](https://github.com/bgentry/speakeasy/blob/v0.1.0/speakeasy_unix.go)、[CLI 認証設定](https://developer.hashicorp.com/terraform/cli/config/config-file#credentials) 。

既存 workspace の場合:

```sh
terraform -chdir=infra import tfe_workspace.prod ORGANIZATION/WORKSPACE
terraform -chdir=infra import tfe_workspace_settings.prod ORGANIZATION/WORKSPACE
terraform -chdir=infra plan
```

## 同じ state を作成した workspace へ移行

1. workspace に sensitive な環境変数 `TFE_TOKEN` を HCP Terraform UI から設定する。自身の workspace 設定・変数を管理できる権限が必要。ローカルの `terraform login` 情報は remote runner に渡らない。token の作成やその値の登録を Terraform resource で管理しない（state への保存を避ける）。
2. `infra/cloud.tf.example` を `infra/cloud.tf` へコピーし、作成済みの organization / workspace 名と一致させる。
3. `terraform -chdir=infra init` を実行し、既存 local state の移行確認に同意する。cloud integration への移行では `-migrate-state` や `-reconfigure` を付けない。
4. `terraform -chdir=infra state list` で workspace / settings / variables が維持されていることを確認する。
5. `terraform -chdir=infra plan` で remote run が成功し、意図しない追加・再作成がないことを確認する。

state 移行のために別 root module や別 state は作らない。local state とそのバックアップにはアクセス制限を設け、移行の確認が済むまで保管する。state・plan・実値 tfvars・ローカル接続用 cloud.tf は Git 除外済み。

VCS 接続は移行完了後に追加する。接続時の working directory は `infra` とし、org / branch / GitHub App などの実値を確認してコード化する。初回は CLI-driven の remote run で動作を確認できる。VCS 接続後は適用を HCP Terraform の VCS workflow から行う。

CLI-driven remote run は、working directory の親となるリポジトリルートをアップロードの起点とする。ルートの `.terraformignore` で `infra/` のみを含め、ローカル state / plan / tfvars / cloud 接続設定を除外する。

## 次の作業

GCP project ID とリージョンを確認し、同じ root module に GCP WIF と plan / apply 用 SA を追加する。GCP の初回 WIF 作成はローカルの管理者認証を使用し、その後は HCP Terraform Dynamic Provider Credentials へ切り替える。静的 SA キーは使わない。

公式資料:

- [tfe_workspace](https://registry.terraform.io/providers/hashicorp/tfe/latest/docs/resources/workspace)
- [Cloud integration と state 移行](https://developer.hashicorp.com/terraform/cli/cloud/settings)
