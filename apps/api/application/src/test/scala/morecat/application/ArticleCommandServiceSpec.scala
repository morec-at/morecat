package morecat.application

import morecat.domain.*
import zio.*
import zio.test.Assertion.*
import zio.test.*

object ArticleCommandServiceSpec extends ZIOSpecDefault:

  private val articleId = ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001")

  private final class RecordingStore(
      initialEvents: Chunk[SequencedArticleEvent] = Chunk.empty,
      createDraftResult: IO[EventStoreError, Unit] = ZIO.unit,
      appendResult: IO[EventStoreError, Unit] = ZIO.unit,
  ) extends ArticleEventStore:
    var created: List[(ArticleId, ArticleDrafted)]            = Nil
    var appended: List[(ArticleId, Long, ArticleEvent)]       = Nil
    def createDraft(articleId: ArticleId, event: ArticleDrafted): IO[EventStoreError, Unit] =
      createDraftResult *> ZIO.succeed {
        created = created :+ (articleId, event)
      }
    def load(articleId: ArticleId): IO[EventStoreError, Chunk[SequencedArticleEvent]] =
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
      test("rejects invalid slug before touching the store") {
        val store   = RecordingStore()
        val service = ArticleCommandService(store, FixedClock(123L))

        for
          exit <- service.createDraft(CreateDraftCommand(articleId, "../bad", "Hello", "body")).exit
        yield assertTrue(exit == Exit.fail(CommandError.InvalidSlug), store.created.isEmpty)
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
      test("does not hide expected version conflicts when the article is already published") {
        val events = Chunk(
          SequencedArticleEvent(1L, ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")),
          SequencedArticleEvent(2L, ArticlePublished(publishedAt = 999L)),
        )
        val store   = RecordingStore(initialEvents = events)
        val service = ArticleCommandService(store, FixedClock(1234L))

        for
          exit <- service.publish(PublishArticleCommand(articleId, expectedVersion = 1L)).exit
        yield assertTrue(exit == Exit.fail(CommandError.VersionConflict), store.appended.isEmpty)
      },
    ),
  )
