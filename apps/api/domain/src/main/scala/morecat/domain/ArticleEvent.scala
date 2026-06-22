package morecat.domain

/** Article のドメインイベント（中粒度・意図整合）。イベントが唯一の真実。
  *
  * 集約 ID はストリームのパス（articles/{id}/events/{seq}）が持つため payload には含めない。
  * payload は `schemaVersion` を持ち、fold 時にアップキャストできるようにする。
  * JSON 等の wire フォーマットは infrastructure 層の関心事であり、domain は関知しない。
  */
sealed trait ArticleEvent:
  def schemaVersion: Int

/** 下書き作成（初期 slug/title/body）。tags は slice2 以降。 */
final case class ArticleDrafted(
    slug: Slug,
    title: Title,
    body: String,
    schemaVersion: Int = 1,
) extends ArticleEvent

/** 公開。publishedAt はサーバ時刻（epoch millis）。 */
final case class ArticlePublished(
    publishedAt: Long,
    schemaVersion: Int = 1,
) extends ArticleEvent
