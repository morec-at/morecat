# triage-pr-reviews

現在のブランチの PR に付いた **bot 自動レビュー**（codex-review の sticky コメント／`chatgpt-codex-connector`／その他 bot）を確認し、各指摘に対応すべきか評価する。**評価の提示までを自動**で行い、**実際のコード修正・push・PR への返信は行わず、必ずユーザーの承認を待つ**。

## 実行環境
この手順は Codex 専用。

- GitHub app / `gh` / `git` / `rg` / ファイル Read を使う。
- GitHub app が使える場合は、PR メタデータ・コメント・レビュー取得は app を優先してよい。
- thread の解決状態など app で不足する情報があれば `gh api` を使う。
- bot レビューが未到着なら、同一ターン内で短くポーリングし、最大待ち時間を超えたら「まだ来ていない」と報告して終了する。

## 対象 PR
- 引数 `$1` があればその PR 番号。無ければ現在ブランチの PR を取得する。
  - `gh`: `gh pr view --json number,headRefOid,url,state,baseRefName,headRefName`
  - GitHub app: PR 番号が分かる場合は PR metadata を取得する。番号が不明なら `gh pr view` を使う。
- PR が無ければその旨を報告して終了。

## 1. bot レビューを待つ（ポーリング）
bot レビューは非同期（codex-review は CI 実行で数分かかる）。**現行 HEAD コミット（`headRefOid`）に対するレビューがまだ無い場合は待つ**。

- 取得元（3 系統すべて）:
  - issue コメント: `gh pr view <n> --json comments` の中の `<!-- codex-review -->` を含むもの（codex sticky）
  - レビュー本体: `gh api repos/{owner}/{repo}/pulls/<n>/reviews`
  - インラインコメント: `gh api repos/{owner}/{repo}/pulls/<n>/comments`
  - GitHub app を使う場合も、同等に issue comments / reviews / inline review comments を取得する。
- 各コメントの `commit_id` / sticky 内の commit ハッシュを見て、**現行 HEAD 向けのレビューが出揃ったか**を判定。
- 出ていなければ `sleep` を挟んで再取得する。**120 秒間隔、最大 ~15 分**を目安にし、超えたら「まだ来ていない」と報告して終了（無限待ちしない）。
- 既に HEAD 向けのレビューがあれば即 2 へ。

## 2. 各指摘を現行 HEAD と突き合わせて評価
**重要**: bot のコメントは**古いコミット時点のコード**に付いていることが多い（ファイル名変更・コード移動・別レイヤへの移管で、指摘箇所が現行 HEAD に存在しないことがある）。必ず現行コードを Read/`rg` で確認してから判定する。各指摘を次のいずれかに分類:

- **要対応** — 現行コードに該当し、修正すべき正当な指摘
- **対応不要（設計意図）** — 既存の設計判断と整合しており、変更不要（根拠を添える。`docs/design.md`・`docs/slice-1-plan.md` を参照）
- **対応不要（陳腐化）** — 指摘箇所が現行 HEAD に存在しない／既に別コミットで解消済み（解消したコミットを示す）
- **曖昧** — 判断に追加情報や設計判断が要る

既に自分が返信済みのスレッド（インラインに自分の reply がある）は再評価しない（重複防止）。

必要に応じて以下も確認する。

- `git status --short` で作業ツリーの未コミット変更を把握し、ユーザーの変更を巻き戻さない。
- `git show <headRefOid> --stat` や PR diff で、bot 指摘と現行 HEAD の差分を対応付ける。
- 設計意図の判断が必要な場合は `docs/design.md` と `docs/slice-1-plan.md` を読む。

## 3. triage サマリを提示してここで停止
各指摘について「分類 / 根拠 / （要対応なら）提案する対応」を一覧で提示する。**ここで停止し、修正・push・返信はユーザーの承認を得てから行う。** 承認されたら:
- 要対応: 修正 → `sbt domain/test` 等の関連テスト → commit/push → 該当スレッドに対応コミットを添えて返信
- 対応不要: 該当スレッドに分類と根拠を返信
- 必要に応じて `docs/` への反映も提案

## 注意
- 外部 PR への投稿（コメント返信）は承認後のみ。
- bot コメントの commit ハッシュと現行 HEAD のズレは必ず明示する（今日の教訓: 古いコミットへのコメントで実 fix が不要なケースがある）。
