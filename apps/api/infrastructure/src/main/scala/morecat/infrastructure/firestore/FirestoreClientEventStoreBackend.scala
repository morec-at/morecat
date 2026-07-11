package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import morecat.domain.ArticleId
import zio.*

final class FirestoreClientEventStoreBackend(client: FirestoreDocumentClient)
    extends FirestoreEventStoreBackend:

  def runTransaction[A](
    effect: FirestoreEventStoreTransaction => IO[EventStoreError, A]
  ): IO[EventStoreError, A] =
    client.transaction(txClient => effect(FirestoreClientEventStoreTransaction(txClient)))

  def loadEvents(articleId: ArticleId): IO[EventStoreError, Chunk[StoredFirestoreArticleEvent]] =
    client
      .listDocuments(FirestoreDocumentModel.articleEventsCollectionPath(articleId))
      .mapError(FirestoreEventStoreErrorMapper.read)
      .flatMap { documents =>
        ZIO
          .foreach(documents) { document =>
            for
              seq <- parseSeq(document.id)
              json <- ZIO
                .fromOption(document.data.get(FirestoreDocumentModel.EventJsonField))
                .orElseFail(
                  EventStoreError.Unavailable(
                    s"missing Firestore field: ${FirestoreDocumentModel.EventJsonField}"
                  )
                )
            yield StoredFirestoreArticleEvent(seq, json)
          }
          .map(_.sortBy(_.seq))
      }

  private def parseSeq(value: String): IO[EventStoreError, Long] =
    ZIO
      .attempt(value.toLong)
      .mapError(_ => EventStoreError.Unavailable(s"invalid event seq document id: $value"))

private final class FirestoreClientEventStoreTransaction(tx: FirestoreDocumentTransaction)
    extends FirestoreEventStoreTransaction:

  def createDocument(
    path: FirestoreDocumentPath,
    data: Map[String, String],
    alreadyExistsError: EventStoreError,
  ): IO[EventStoreError, Unit] =
    tx.create(path, data).mapError(FirestoreEventStoreErrorMapper.create(alreadyExistsError))
