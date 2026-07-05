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
final case class ArticleDrafted private (
  slug: Slug,
  title: Title,
  body: ArticleBody,
) extends ArticleEvent:
  val schemaVersion: Int = ArticleDrafted.CurrentSchemaVersion

object ArticleDrafted:
  val CurrentSchemaVersion: Int = 1

  enum ValidationError:
    case InvalidSlug
    case InvalidTitle
    case BodyTooLarge

  def either(
    slug: String,
    title: String,
    body: String,
  ): Either[Seq[ValidationError], ArticleDrafted] =
    val refinedSlug = Slug.either(slug).left.map(_ => ValidationError.InvalidSlug)
    val refinedTitle = Title.either(title).left.map(_ => ValidationError.InvalidTitle)
    val refinedBody = ArticleBody.either(body).left.map(_ => ValidationError.BodyTooLarge)
    val errors =
      List(refinedSlug.left.toOption, refinedTitle.left.toOption, refinedBody.left.toOption).flatten

    if errors.nonEmpty then Left(errors)
    else
      Right(
        ArticleDrafted(
          refinedSlug.toOption.get,
          refinedTitle.toOption.get,
          refinedBody.toOption.get
        )
      )

  def fromStoredEvent(
    slug: String,
    title: String,
    body: String,
  ): Either[Seq[ValidationError], ArticleDrafted] =
    val refinedSlug = Slug.either(slug).left.map(_ => ValidationError.InvalidSlug)
    val refinedTitle = Title.either(title).left.map(_ => ValidationError.InvalidTitle)
    val errors =
      List(refinedSlug.left.toOption, refinedTitle.left.toOption).flatten

    if errors.nonEmpty then Left(errors)
    else
      Right(
        ArticleDrafted(
          refinedSlug.toOption.get,
          refinedTitle.toOption.get,
          ArticleBody.fromStoredEvent(body)
        )
      )

  def applyUnsafe(slug: String, title: String, body: String): ArticleDrafted =
    either(slug, title, body).fold(
      errors => throw IllegalArgumentException(errors.mkString(",")),
      identity,
    )

/** 公開。publishedAt はサーバ時刻（epoch millis）。 */
final case class ArticlePublished(
  publishedAt: Long,
) extends ArticleEvent:
  val schemaVersion: Int = ArticlePublished.CurrentSchemaVersion

object ArticlePublished:
  val CurrentSchemaVersion: Int = 1
