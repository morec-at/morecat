package morecat.domain

import io.github.iltotore.iron.zioJson.given
import zio.json.*

/** Article の公開状態。slice1 は draft/published の 2 状態のみ。 */
enum ArticleStatus:
  case Draft
  case Published

object ArticleStatus:
  given JsonCodec[ArticleStatus] =
    JsonCodec(summon[JsonEncoder[String]], summon[JsonDecoder[String]]).transformOrFail(
      {
        case "draft"     => Right(Draft)
        case "published" => Right(Published)
        case other       => Left(s"unknown article status: $other")
      },
      {
        case Draft     => "draft"
        case Published => "published"
      },
    )

/** Article のドメインイベント（中粒度・意図整合）。イベントが唯一の真実。
  * 集約 ID はストリームのパス（articles/{id}/events/{seq}）が持つため payload には含めない。
  * payload は `schemaVersion` を持ち、fold 時にアップキャストできるようにする。
  */
@jsonDiscriminator("type")
sealed trait ArticleEvent:
  def schemaVersion: Int

object ArticleEvent:
  given JsonCodec[ArticleEvent] = DeriveJsonCodec.gen[ArticleEvent]

/** 下書き作成（初期 slug/title/body）。tags は slice2 以降。 */
@jsonHint("ArticleDrafted")
final case class ArticleDrafted(
    slug: Slug,
    title: NonEmptyTitle,
    body: String,
    schemaVersion: Int = 1,
) extends ArticleEvent

/** 公開。publishedAt はサーバ時刻（epoch millis）。 */
@jsonHint("ArticlePublished")
final case class ArticlePublished(
    publishedAt: Long,
    schemaVersion: Int = 1,
) extends ArticleEvent

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
  given JsonCodec[Article] = DeriveJsonCodec.gen[Article]

  /** Article イベントストリームを畳み込み投影を構築する純粋関数。
    * 順序付きの完全ストリームを渡す前提（RMU は全ストリームを再読込して fold する）。
    */
  def fold(id: ArticleId, events: Iterable[ArticleEvent]): Option[Article] =
    events.foldLeft(Option.empty[Article])((state, event) => applyEvent(id, state, event))

  def applyEvent(id: ArticleId, state: Option[Article], event: ArticleEvent): Option[Article] =
    event match
      case d: ArticleDrafted =>
        // 既存があれば下書き作成は無視（最初の Drafted のみ有効）
        state.orElse(Some(Article(id, d.slug, d.title, d.body, ArticleStatus.Draft, None)))
      case p: ArticlePublished =>
        // Drafted 前の Published は state=None のため無視される
        state.map(_.copy(status = ArticleStatus.Published, publishedAt = Some(p.publishedAt)))
