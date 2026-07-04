package morecat.infrastructure.json

import morecat.domain.*
import zio.json.*

object ArticleEventJsonCodec:

  def encode(event: ArticleEvent): String =
    toWire(event).toJson

  def decode(json: String): Either[String, ArticleEvent] =
    json.fromJson[WireArticleEvent].flatMap(toDomain)

  private def toWire(event: ArticleEvent): WireArticleEvent =
    event match
      case ArticleDrafted(slug, title, body) =>
        WireArticleEvent(
          eventType = "ArticleDrafted",
          schemaVersion = ArticleDrafted.CurrentSchemaVersion,
          slug = Some(slug),
          title = Some(title),
          body = Some(body),
          publishedAt = None,
        )
      case ArticlePublished(publishedAt) =>
        WireArticleEvent(
          eventType = "ArticlePublished",
          schemaVersion = ArticlePublished.CurrentSchemaVersion,
          slug = None,
          title = None,
          body = None,
          publishedAt = Some(publishedAt),
        )

  private def toDomain(wire: WireArticleEvent): Either[String, ArticleEvent] =
    wire.eventType match
      case "ArticleDrafted" =>
        for
          _ <- requireSchemaVersion(wire.schemaVersion, ArticleDrafted.CurrentSchemaVersion)
          slug <- required("slug", wire.slug).flatMap(Slug.either)
          title <- required("title", wire.title).flatMap(Title.either)
          body <- required("body", wire.body)
          _ <- absent("publishedAt", wire.publishedAt)
        yield ArticleDrafted(slug, title, body)
      case "ArticlePublished" =>
        for
          _ <- requireSchemaVersion(wire.schemaVersion, ArticlePublished.CurrentSchemaVersion)
          _ <- absent("slug", wire.slug)
          _ <- absent("title", wire.title)
          _ <- absent("body", wire.body)
          publishedAt <- required("publishedAt", wire.publishedAt)
        yield ArticlePublished(publishedAt)
      case other =>
        Left(s"unsupported eventType: $other")

  private def requireSchemaVersion(actual: Int, expected: Int): Either[String, Unit] =
    Either.cond(actual == expected, (), s"unsupported schemaVersion: $actual")

  private def required[A](field: String, value: Option[A]): Either[String, A] =
    value.toRight(s"missing required field: $field")

  private def absent[A](field: String, value: Option[A]): Either[String, Unit] =
    Either.cond(value.isEmpty, (), s"unexpected field for event type: $field")

@jsonNoExtraFields
private final case class WireArticleEvent(
  eventType: String,
  schemaVersion: Int,
  slug: Option[String],
  title: Option[String],
  body: Option[String],
  publishedAt: Option[Long],
) derives JsonCodec
