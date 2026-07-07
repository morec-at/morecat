package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import morecat.domain.ArticleId
import zio.*

final case class StoredFirestoreArticleEvent(seq: Long, json: String)

trait FirestoreEventStoreTransaction:
  def createDocument(
    path: FirestoreDocumentPath,
    data: Map[String, String],
    alreadyExistsError: EventStoreError,
  ): IO[EventStoreError, Unit]

trait FirestoreEventStoreBackend:
  def runTransaction[A](
    effect: FirestoreEventStoreTransaction => IO[EventStoreError, A]
  ): IO[EventStoreError, A]

  def loadEvents(articleId: ArticleId): IO[EventStoreError, Chunk[StoredFirestoreArticleEvent]]
