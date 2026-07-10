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
      .mapError(toEventStoreError)
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

  private def toEventStoreError(error: FirestoreClientError): EventStoreError =
    error match
      case FirestoreClientError.AlreadyExists =>
        EventStoreError.Unavailable("unexpected Firestore create conflict")
      case FirestoreClientError.PermissionDenied(message) =>
        EventStoreError.Unavailable(s"firestore permission denied: $message")
      case FirestoreClientError.InvalidArgument(message) =>
        EventStoreError.Unavailable(s"invalid Firestore request: $message")
      case FirestoreClientError.Unavailable(message) =>
        EventStoreError.Unavailable(message)

private final class FirestoreClientEventStoreTransaction(tx: FirestoreDocumentTransaction)
    extends FirestoreEventStoreTransaction:

  def createDocument(
    path: FirestoreDocumentPath,
    data: Map[String, String],
    alreadyExistsError: EventStoreError,
  ): IO[EventStoreError, Unit] =
    tx.create(path, data).mapError {
      case FirestoreClientError.AlreadyExists =>
        alreadyExistsError
      case FirestoreClientError.PermissionDenied(message) =>
        EventStoreError.Unavailable(s"firestore permission denied: $message")
      case FirestoreClientError.InvalidArgument(message) =>
        EventStoreError.Unavailable(s"invalid Firestore request: $message")
      case FirestoreClientError.Unavailable(message) =>
        EventStoreError.Unavailable(message)
    }
