package morecat.domain

/** Article の公開状態。slice1 は draft/published の 2 状態のみ。 */
enum ArticleStatus:
  case Draft
  case Published

/** 読み取りモデル（プロジェクション）。Cloud SQL に upsert される投影の素。 */
final case class Article(
    id: ArticleId,
    slug: Slug,
    title: NonEmptyTitle,
    body: String,
    status: ArticleStatus,
    publishedAt: Option[Long],
)

object Article:
  /** Article イベントストリームを畳み込み投影を構築する純粋関数。
    * 順序付きの完全ストリームを渡す前提（`Seq` で順序を型に明示）。
    */
  def fold(id: ArticleId, events: Seq[ArticleEvent]): Option[Article] =
    events.foldLeft(Option.empty[Article])((state, event) => applyEvent(id, state, event))

  def applyEvent(id: ArticleId, state: Option[Article], event: ArticleEvent): Option[Article] =
    event match
      case d: ArticleDrafted =>
        // 既存があれば下書き作成は無視（最初の Drafted のみ有効）
        state.orElse(Some(Article(id, d.slug, d.title, d.body, ArticleStatus.Draft, None)))
      case p: ArticlePublished =>
        // Drafted 前の Published は state=None のため無視される
        state.map(_.copy(status = ArticleStatus.Published, publishedAt = Some(p.publishedAt)))
