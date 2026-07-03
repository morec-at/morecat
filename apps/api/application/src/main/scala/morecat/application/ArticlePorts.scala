package morecat.application

import morecat.domain.*
import zio.*

final case class SequencedArticleEvent(seq: Long, event: ArticleEvent)

enum EventStoreError:
  case SlugAlreadyReserved
  case VersionConflict
  case Unavailable(message: String)

trait ArticleEventStore:
  def createDraft(articleId: ArticleId, event: ArticleDrafted): IO[EventStoreError, Unit]
  def load(articleId: ArticleId): IO[EventStoreError, Chunk[SequencedArticleEvent]]
  def append(articleId: ArticleId, expectedVersion: Long, event: ArticleEvent): IO[EventStoreError, Unit]

trait ServerClock:
  def nowMillis: UIO[Long]

final case class PublishedArticle(
    id: ArticleId,
    slug: Slug,
    title: Title,
    body: String,
    publishedAt: Long,
)

trait PublishedArticleQuery:
  def findBySlug(slug: Slug): IO[QueryError, Option[PublishedArticle]]

enum QueryError:
  case Unavailable(message: String)
