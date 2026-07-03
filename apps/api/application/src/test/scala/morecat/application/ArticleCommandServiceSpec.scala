package morecat.application

import morecat.domain.*
import zio.*
import zio.test.Assertion.*
import zio.test.*

object ArticleCommandServiceSpec extends ZIOSpecDefault:

  private val articleId = ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001")

  private final class RecordingStore(
      initialEvents: Chunk[SequencedArticleEvent] = Chunk.empty,
      loadResults: List[IO[EventStoreError, Chunk[SequencedArticleEvent]]] = Nil,
      createDraftResult: IO[EventStoreError, Unit] = ZIO.unit,
      appendResult: IO[EventStoreError, Unit] = ZIO.unit,
  ) extends ArticleEventStore:
    var created: List[(ArticleId, ArticleDrafted)]            = Nil
    var appended: List[(ArticleId, Long, ArticleEvent)]       = Nil
    private var remainingLoads = loadResults
    def createDraft(articleId: ArticleId, event: ArticleDrafted): IO[EventStoreError, Unit] =
      createDraftResult *> ZIO.succeed {
        created = created :+ (articleId, event)
      }
    def load(articleId: ArticleId): IO[EventStoreError, Chunk[SequencedArticleEvent]] =
      remainingLoads match
        case next :: rest =>
          remainingLoads = rest
          next
        case Nil =>
          ZIO.succeed(initialEvents)
    def append(articleId: ArticleId, expectedVersion: Long, event: ArticleEvent): IO[EventStoreError, Unit] =
      appendResult *> ZIO.succeed {
        appended = appended :+ (articleId, expectedVersion, event)
      }

  private final class FixedClock(value: Long) extends ServerClock:
    def nowMillis: UIO[Long] = ZIO.succeed(value)

  def spec = suite("ArticleCommandService")(
    suite("createDraft")(
      test("creates an ArticleDrafted event with validated slug and title") {
        val store   = RecordingStore()
        val service = ArticleCommandService(store, FixedClock(123L))

        for
          _ <- service.createDraft(CreateDraftCommand(articleId, "hello-world", "Hello", "body"))
        yield assertTrue(
          store.created == List(
            (articleId, ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")),
          ),
        )
      },
      test("maps slug reservation conflicts to SlugConflict") {
        val store   = RecordingStore(createDraftResult = ZIO.fail(EventStoreError.SlugAlreadyReserved))
        val service = ArticleCommandService(store, FixedClock(123L))

        assertZIO(service.createDraft(CreateDraftCommand(articleId, "hello-world", "Hello", "body")).exit)(
          fails(equalTo(CommandError.SlugConflict)),
        )
      },
      test("maps store unavailability while loading to StoreUnavailable") {
        val store = RecordingStore(loadResults = List(ZIO.fail(EventStoreError.Unavailable("down"))))
        val service = ArticleCommandService(store, FixedClock(123L))

        assertZIO(service.createDraft(CreateDraftCommand(articleId, "hello-world", "Hello", "body")).exit)(
          fails(equalTo(CommandError.StoreUnavailable("down"))),
        )
      },
      test("rejects invalid slug before touching the store") {
        val store   = RecordingStore()
        val service = ArticleCommandService(store, FixedClock(123L))

        for
          exit <- service.createDraft(CreateDraftCommand(articleId, "../bad", "Hello", "body")).exit
        yield assertTrue(exit == Exit.fail(CommandError.InvalidSlug), store.created.isEmpty)
      },
      test("is idempotent for the same articleId and same draft content") {
        val drafted = ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")
        val store   = RecordingStore(initialEvents = Chunk(SequencedArticleEvent(seq = 1L, event = drafted)))
        val service = ArticleCommandService(store, FixedClock(123L))

        for
          _ <- service.createDraft(CreateDraftCommand(articleId, "hello-world", "Hello", "body"))
        yield assertTrue(store.created.isEmpty)
      },
      test("rejects the same articleId with different draft content") {
        val drafted = ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")
        val store   = RecordingStore(initialEvents = Chunk(SequencedArticleEvent(seq = 1L, event = drafted)))
        val service = ArticleCommandService(store, FixedClock(123L))

        assertZIO(service.createDraft(CreateDraftCommand(articleId, "hello-world", "Changed", "body")).exit)(
          fails(equalTo(CommandError.VersionConflict)),
        )
      },
      test("treats create conflict as idempotent when reload shows the same draft") {
        val drafted = ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")
        val store = RecordingStore(
          loadResults = List(
            ZIO.succeed(Chunk.empty),
            ZIO.succeed(Chunk(SequencedArticleEvent(seq = 1L, event = drafted))),
          ),
          createDraftResult = ZIO.fail(EventStoreError.VersionConflict),
        )
        val service = ArticleCommandService(store, FixedClock(123L))

        for
          _ <- service.createDraft(CreateDraftCommand(articleId, "hello-world", "Hello", "body"))
        yield assertTrue(store.created.isEmpty)
      },
    ),
    suite("publish")(
      test("fails with ArticleNotFound when the stream does not exist") {
        val store   = RecordingStore()
        val service = ArticleCommandService(store, FixedClock(123L))

        assertZIO(service.publish(PublishArticleCommand(articleId, expectedVersion = 1L)).exit)(
          fails(equalTo(CommandError.ArticleNotFound)),
        )
      },
      test("appends ArticlePublished with server time and expected version") {
        val drafted = SequencedArticleEvent(
          seq = 1L,
          event = ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body"),
        )
        val store   = RecordingStore(initialEvents = Chunk(drafted))
        val service = ArticleCommandService(store, FixedClock(999L))

        for
          result <- service.publish(PublishArticleCommand(articleId, expectedVersion = 1L))
        yield assertTrue(
          result == PublishResult.Published,
          store.appended == List((articleId, 1L, ArticlePublished(publishedAt = 999L))),
        )
      },
      test("maps expected version conflicts without hiding them") {
        val drafted = SequencedArticleEvent(
          seq = 1L,
          event = ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body"),
        )
        val store = RecordingStore(
          initialEvents = Chunk(drafted),
          appendResult = ZIO.fail(EventStoreError.VersionConflict),
        )
        val service = ArticleCommandService(store, FixedClock(999L))

        assertZIO(service.publish(PublishArticleCommand(articleId, expectedVersion = 0L)).exit)(
          fails(equalTo(CommandError.VersionConflict)),
        )
      },
      test("maps store unavailability while loading to StoreUnavailable") {
        val store = RecordingStore(loadResults = List(ZIO.fail(EventStoreError.Unavailable("down"))))
        val service = ArticleCommandService(store, FixedClock(999L))

        assertZIO(service.publish(PublishArticleCommand(articleId, expectedVersion = 1L)).exit)(
          fails(equalTo(CommandError.StoreUnavailable("down"))),
        )
      },
      test("is idempotent when the article is already published") {
        val events = Chunk(
          SequencedArticleEvent(1L, ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")),
          SequencedArticleEvent(2L, ArticlePublished(publishedAt = 999L)),
        )
        val store   = RecordingStore(initialEvents = events)
        val service = ArticleCommandService(store, FixedClock(1234L))

        for
          result <- service.publish(PublishArticleCommand(articleId, expectedVersion = 2L))
        yield assertTrue(result == PublishResult.AlreadyPublished, store.appended.isEmpty)
      },
      test("is idempotent when retrying the original publish command after success") {
        val events = Chunk(
          SequencedArticleEvent(1L, ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")),
          SequencedArticleEvent(2L, ArticlePublished(publishedAt = 999L)),
        )
        val store   = RecordingStore(initialEvents = events)
        val service = ArticleCommandService(store, FixedClock(1234L))

        for
          result <- service.publish(PublishArticleCommand(articleId, expectedVersion = 1L))
        yield assertTrue(result == PublishResult.AlreadyPublished, store.appended.isEmpty)
      },
      test("does not hide unrelated expected version conflicts when the article is already published") {
        val events = Chunk(
          SequencedArticleEvent(1L, ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")),
          SequencedArticleEvent(2L, ArticlePublished(publishedAt = 999L)),
        )
        val store   = RecordingStore(initialEvents = events)
        val service = ArticleCommandService(store, FixedClock(1234L))

        for
          exit <- service.publish(PublishArticleCommand(articleId, expectedVersion = 0L)).exit
        yield assertTrue(exit == Exit.fail(CommandError.VersionConflict), store.appended.isEmpty)
      },
      test("treats append conflict as idempotent when reload shows the publish succeeded") {
        val drafted = SequencedArticleEvent(
          seq = 1L,
          event = ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body"),
        )
        val published = SequencedArticleEvent(seq = 2L, event = ArticlePublished(publishedAt = 999L))
        val store = RecordingStore(
          loadResults = List(
            ZIO.succeed(Chunk(drafted)),
            ZIO.succeed(Chunk(drafted, published)),
          ),
          appendResult = ZIO.fail(EventStoreError.VersionConflict),
        )
        val service = ArticleCommandService(store, FixedClock(999L))

        assertZIO(service.publish(PublishArticleCommand(articleId, expectedVersion = 1L)))(
          equalTo(PublishResult.Published),
        )
      },
    ),
  )
