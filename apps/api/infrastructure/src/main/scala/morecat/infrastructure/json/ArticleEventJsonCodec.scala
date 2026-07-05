package morecat.infrastructure.json

import morecat.domain.*
import zio.json.*

object ArticleEventJsonCodec:

  def encode(event: ArticleEvent): String =
    event match
      case ArticleDrafted(slug, title, body) =>
        WireArticleDrafted(
          eventType = "ArticleDrafted",
          schemaVersion = ArticleDrafted.CurrentSchemaVersion,
          slug = slug.toString,
          title = title.toString,
          body = body.value,
        ).toJson
      case ArticlePublished(publishedAt) =>
        WireArticlePublished(
          eventType = "ArticlePublished",
          schemaVersion = ArticlePublished.CurrentSchemaVersion,
          publishedAt = publishedAt,
        ).toJson

  def decode(json: String): Either[String, ArticleEvent] =
    json
      .fromJson[WireArticleHeader]
      .flatMap: header =>
        header.eventType match
          case "ArticleDrafted" =>
            json.fromJson[WireArticleDrafted].flatMap(toDomain)
          case "ArticlePublished" =>
            json.fromJson[WireArticlePublished].flatMap(toDomain)
          case other =>
            Left(s"unsupported eventType: $other")

  private def toDomain(wire: WireArticleDrafted): Either[String, ArticleEvent] =
    for
      _ <- requireSchemaVersion(wire.schemaVersion, ArticleDrafted.CurrentSchemaVersion)
      event <- ArticleDrafted
        .fromStoredEvent(wire.slug, wire.title, wire.body)
        .left
        .map(_.mkString(","))
    yield event

  private def toDomain(wire: WireArticlePublished): Either[String, ArticleEvent] =
    for _ <- requireSchemaVersion(wire.schemaVersion, ArticlePublished.CurrentSchemaVersion)
    yield ArticlePublished(wire.publishedAt)

  private def requireSchemaVersion(actual: Int, expected: Int): Either[String, Unit] =
    Either.cond(actual == expected, (), s"unsupported schemaVersion: $actual")

private final case class WireArticleHeader(eventType: String) derives JsonDecoder

@jsonNoExtraFields
private final case class WireArticleDrafted(
  eventType: String,
  schemaVersion: Int,
  slug: String,
  title: String,
  body: String,
) derives JsonCodec

@jsonNoExtraFields
private final case class WireArticlePublished(
  eventType: String,
  schemaVersion: Int,
  publishedAt: Long,
) derives JsonCodec
