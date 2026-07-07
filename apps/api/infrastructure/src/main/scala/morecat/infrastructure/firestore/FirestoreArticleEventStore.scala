package morecat.infrastructure.firestore

import morecat.application.*
import morecat.domain.*
import morecat.infrastructure.json.ArticleEventJsonCodec
import zio.*

final class FirestoreArticleEventStore(backend: FirestoreEventStoreBackend)
    extends ArticleEventStore:

  def createDraft(articleId: ArticleId, event: ArticleDrafted): IO[EventStoreError, Unit] =
    backend.runTransaction { tx =>
      tx.createSlugReservation(event.slug, articleId) *>
        tx.createArticleEvent(articleId, seq = 1L, ArticleEventJsonCodec.encode(event))
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
      tx.createArticleEvent(
        articleId,
        seq = expectedVersion + 1L,
        ArticleEventJsonCodec.encode(event),
      )
    }
