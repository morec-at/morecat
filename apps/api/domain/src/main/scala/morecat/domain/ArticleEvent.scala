package morecat.domain

/**
 * Article のドメインイベント（中粒度・意図整合）。イベントが唯一の真実。
 *
 * 集約 ID はストリームのパス（articles/{id}/events/{seq}）が持つため payload には含めない。 `schemaVersion`
 * は各イベント型の**現行スキーマ版**を表す固定値。新規生成イベントは常に 現行版であり、古い版は infrastructure の decode/upcast 時に wire
 * 上でのみ存在する。 JSON 等の wire フォーマットは infrastructure 層の関心事であり、domain は関知しない。
 */
sealed trait ArticleEvent:
  def schemaVersion: Int

/** 下書き作成（初期 slug/title/body）。tags は slice2 以降。 */
final case class ArticleDrafted(
  slug: Slug,
  title: Title,
  body: String,
) extends ArticleEvent:
  val schemaVersion: Int = ArticleDrafted.CurrentSchemaVersion

object ArticleDrafted:
  val CurrentSchemaVersion: Int = 1

/** 公開。publishedAt はサーバ時刻（epoch millis）。 */
final case class ArticlePublished(
  publishedAt: Long,
) extends ArticleEvent:
  val schemaVersion: Int = ArticlePublished.CurrentSchemaVersion

object ArticlePublished:
  val CurrentSchemaVersion: Int = 1
