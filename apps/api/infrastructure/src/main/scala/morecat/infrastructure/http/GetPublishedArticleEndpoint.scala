package morecat.infrastructure.http

import morecat.application.*
import morecat.domain.*
import sttp.model.StatusCode
import sttp.tapir.Schema
import sttp.tapir.json.zio.*
import sttp.tapir.ztapir.*
import zio.*
import zio.json.*

final case class PublishedArticleResponse(
  articleId: String,
  slug: String,
  title: String,
  body: String,
  publishedAt: Long,
) derives JsonCodec,
      Schema

final class GetPublishedArticleEndpoint(
  getBySlug: String => IO[PublishedArticleError, PublishedArticle]
):
  val endpoint = GetPublishedArticleEndpoint.endpoint
    .zServerLogic[Any](rawSlug => execute(rawSlug).flatMap(ZIO.fromEither))

  def execute(rawSlug: String): UIO[Either[StatusCode, PublishedArticleResponse]] =
    getBySlug(rawSlug)
      .map(toResponse)
      .mapError(toStatusCode)
      .either

  private def toStatusCode(error: PublishedArticleError): StatusCode =
    error match
      case PublishedArticleError.InvalidSlug      => StatusCode.BadRequest
      case PublishedArticleError.NotFound         => StatusCode.NotFound
      case PublishedArticleError.QueryUnavailable => StatusCode.ServiceUnavailable

  private def toResponse(article: PublishedArticle): PublishedArticleResponse =
    PublishedArticleResponse(
      articleId = article.id.asString,
      slug = article.slug,
      title = article.title,
      body = article.body,
      publishedAt = article.publishedAt,
    )

object GetPublishedArticleEndpoint:
  val endpoint = sttp.tapir.ztapir.endpoint.get
    .in("articles" / path[String]("slug"))
    .out(jsonBody[PublishedArticleResponse])
    .errorOut(
      statusCode
        .description(StatusCode.BadRequest, "Invalid slug")
        .description(StatusCode.NotFound, "Published article not found")
        .description(StatusCode.ServiceUnavailable, "Service unavailable")
    )
