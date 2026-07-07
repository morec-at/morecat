package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import morecat.domain.*
import zio.*

final case class StoredFirestoreArticleEvent(seq: Long, json: String)

trait FirestoreEventStoreTransaction:
  def createSlugReservation(slug: Slug, articleId: ArticleId): IO[EventStoreError, Unit]
  def createArticleEvent(articleId: ArticleId, seq: Long, json: String): IO[EventStoreError, Unit]

trait FirestoreEventStoreBackend:
  def runTransaction[A](
    effect: FirestoreEventStoreTransaction => IO[EventStoreError, A]
  ): IO[EventStoreError, A]

  def loadEvents(articleId: ArticleId): IO[EventStoreError, Chunk[StoredFirestoreArticleEvent]]
