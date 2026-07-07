package morecat.infrastructure.firestore

import morecat.application.*
import morecat.domain.*
import morecat.infrastructure.json.ArticleEventJsonCodec
import zio.*

final class FirestoreArticleEventStore(backend: FirestoreEventStoreBackend)
    extends ArticleEventStore:

  def createDraft(articleId: ArticleId, event: ArticleDrafted): IO[EventStoreError, Unit] =
    backend.runTransaction { tx =>
      tx.createDocument(
        FirestoreDocumentModel.slugReservationPath(event.slug),
        FirestoreDocumentModel.slugReservationData(articleId),
        EventStoreError.SlugAlreadyReserved,
      ) *>
        tx.createDocument(
          FirestoreDocumentModel.articleEventPath(articleId, seq = 1L),
          FirestoreDocumentModel.articleEventData(ArticleEventJsonCodec.encode(event)),
          EventStoreError.VersionConflict,
        )
    }

  def load(articleId: ArticleId): IO[EventStoreError, Chunk[SequencedArticleEvent]] =
    backend
      .loadEvents(articleId)
      .flatMap { storedEvents =>
        ZIO.foreach(storedEvents.sortBy(_.seq)) { stored =>
          ZIO
            .fromEither(ArticleEventJsonCodec.decode(stored.json))
            .map(event => SequencedArticleEvent(stored.seq, event))
            .mapError(error => EventStoreError.Unavailable(error))
        }
      }

  def append(
    articleId: ArticleId,
    expectedVersion: Long,
    event: ArticleEvent
  ): IO[EventStoreError, Unit] =
    backend.runTransaction { tx =>
      tx.createDocument(
        FirestoreDocumentModel.articleEventPath(articleId, seq = expectedVersion + 1L),
        FirestoreDocumentModel.articleEventData(ArticleEventJsonCodec.encode(event)),
        EventStoreError.VersionConflict,
      )
    }
