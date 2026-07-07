package morecat.infrastructure.firestore

import morecat.application.*
import morecat.domain.*
import morecat.infrastructure.json.ArticleEventJsonCodec
import zio.*
import zio.test.*

object FirestoreArticleEventStoreSpec extends ZIOSpecDefault:

  private val articleId =
    ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001")
  private val otherArticleId =
    ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000002")
  private val draft =
    ArticleDrafted.applyUnsafe("hello-world", "Hello", "body")

  def spec = suite("FirestoreArticleEventStore")(
    test("createDraft reserves the slug and creates seq 1 in one transaction") {
      val backend = InMemoryFirestoreEventStoreBackend.empty
      val store = FirestoreArticleEventStore(backend)

      for
        _ <- store.createDraft(articleId, draft)
        events <- store.load(articleId)
      yield assertTrue(
        backend.slugOwner("hello-world") == Some(articleId.asString),
        events == Chunk(SequencedArticleEvent(1L, draft)),
      )
    },
    test("createDraft rolls back the slug reservation when seq 1 already exists") {
      val backend = InMemoryFirestoreEventStoreBackend.empty
        .withEvent(articleId, 1L, ArticlePublished(publishedAt = 999L))
      val store = FirestoreArticleEventStore(backend)

      for exit <- store.createDraft(articleId, draft).exit
      yield assertTrue(
        exit == Exit.fail(EventStoreError.VersionConflict),
        backend.slugOwner("hello-world").isEmpty,
      )
    },
    test("createDraft rolls back the event when the slug is already reserved") {
      val backend = InMemoryFirestoreEventStoreBackend.empty
        .withSlug("hello-world", otherArticleId)
      val store = FirestoreArticleEventStore(backend)

      for
        exit <- store.createDraft(articleId, draft).exit
        events <- store.load(articleId)
      yield assertTrue(
        exit == Exit.fail(EventStoreError.SlugAlreadyReserved),
        events.isEmpty,
      )
    },
    test("append creates expectedVersion plus one") {
      val backend = InMemoryFirestoreEventStoreBackend.empty
        .withEvent(articleId, 1L, draft)
      val store = FirestoreArticleEventStore(backend)

      for
        _ <- store.append(articleId, expectedVersion = 1L, ArticlePublished(publishedAt = 999L))
        events <- store.load(articleId)
      yield assertTrue(
        events == Chunk(
          SequencedArticleEvent(1L, draft),
          SequencedArticleEvent(2L, ArticlePublished(publishedAt = 999L)),
        )
      )
    },
    test("append maps an existing target seq to VersionConflict") {
      val backend = InMemoryFirestoreEventStoreBackend.empty
        .withEvent(articleId, 1L, draft)
        .withEvent(articleId, 2L, ArticlePublished(publishedAt = 999L))
      val store = FirestoreArticleEventStore(backend)

      assertZIO(
        store.append(articleId, expectedVersion = 1L, ArticlePublished(publishedAt = 1000L)).exit
      )(
        Assertion.fails(Assertion.equalTo(EventStoreError.VersionConflict))
      )
    },
  )

private final class InMemoryFirestoreEventStoreBackend private (
  private var slugs: Map[String, String],
  private var events: Map[String, Map[Long, String]],
) extends FirestoreEventStoreBackend:

  def withSlug(slug: String, articleId: ArticleId): InMemoryFirestoreEventStoreBackend =
    slugs = slugs.updated(slug, articleId.asString)
    this

  def withEvent(
    articleId: ArticleId,
    seq: Long,
    event: ArticleEvent,
  ): InMemoryFirestoreEventStoreBackend =
    val articleEvents = events.getOrElse(articleId.asString, Map.empty)
    events = events.updated(
      articleId.asString,
      articleEvents.updated(seq, ArticleEventJsonCodec.encode(event)),
    )
    this

  def slugOwner(slug: String): Option[String] =
    slugs.get(slug)

  def runTransaction[A](
    effect: FirestoreEventStoreTransaction => IO[EventStoreError, A]
  ): IO[EventStoreError, A] =
    for
      stagedSlugs <- Ref.make(slugs)
      stagedEvents <- Ref.make(events)
      result <- effect(InMemoryTransaction(stagedSlugs, stagedEvents)).tap { _ =>
        for
          committedSlugs <- stagedSlugs.get
          committedEvents <- stagedEvents.get
          _ <- ZIO.succeed {
            slugs = committedSlugs
            events = committedEvents
          }
        yield ()
      }
    yield result

  def loadEvents(articleId: ArticleId): IO[EventStoreError, Chunk[StoredFirestoreArticleEvent]] =
    ZIO.succeed {
      Chunk.fromIterable(
        events
          .getOrElse(articleId.asString, Map.empty)
          .toSeq
          .map((seq, json) => StoredFirestoreArticleEvent(seq, json))
      )
    }

private object InMemoryFirestoreEventStoreBackend:
  def empty: InMemoryFirestoreEventStoreBackend =
    InMemoryFirestoreEventStoreBackend(Map.empty, Map.empty)

private final class InMemoryTransaction(
  slugs: Ref[Map[String, String]],
  events: Ref[Map[String, Map[Long, String]]],
) extends FirestoreEventStoreTransaction:

  def createSlugReservation(slug: Slug, articleId: ArticleId): IO[EventStoreError, Unit] =
    slugs.modify { current =>
      val slugValue = slug.toString
      if current.contains(slugValue) then (ZIO.fail(EventStoreError.SlugAlreadyReserved), current)
      else (ZIO.unit, current.updated(slugValue, articleId.asString))
    }.flatten

  def createArticleEvent(
    articleId: ArticleId,
    seq: Long,
    json: String,
  ): IO[EventStoreError, Unit] =
    events.modify { current =>
      val articleEvents = current.getOrElse(articleId.asString, Map.empty)
      if articleEvents.contains(seq) then (ZIO.fail(EventStoreError.VersionConflict), current)
      else
        (
          ZIO.unit,
          current.updated(articleId.asString, articleEvents.updated(seq, json)),
        )
    }.flatten
