package morecat.application

import morecat.domain.*
import zio.*

enum PublishedArticleError:
  case InvalidSlug
  case NotFound
  case QueryUnavailable(message: String)

final class PublishedArticleService(query: PublishedArticleQuery):

  def getBySlug(rawSlug: String): IO[PublishedArticleError, PublishedArticle] =
    for
      slug <- ZIO.fromEither(Slug.either(rawSlug)).mapError(_ => PublishedArticleError.InvalidSlug)
      article <- query.findBySlug(slug).mapError(toPublishedArticleError)
      result <- ZIO.fromOption(article).orElseFail(PublishedArticleError.NotFound)
    yield result

  private def toPublishedArticleError(error: QueryError): PublishedArticleError =
    error match
      case QueryError.Unavailable(message) => PublishedArticleError.QueryUnavailable(message)
