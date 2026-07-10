package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import morecat.domain.ArticleId
import zio.*
import zio.test.*

object FirestoreClientEventStoreBackendSpec extends ZIOSpecDefault:

  private val articleId =
    ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001")

  def spec = suite("FirestoreClientEventStoreBackend")(
    test("createDocument delegates path and data inside a transaction") {
      val client = RecordingFirestoreDocumentClient()
      val backend = FirestoreClientEventStoreBackend(client)
      val path = FirestoreDocumentPath("slugs", "hello-world")
      val data = Map("articleId" -> articleId.asString)

      for _ <- backend.runTransaction(
          _.createDocument(path, data, EventStoreError.SlugAlreadyReserved)
        )
      yield assertTrue(
        client.transactionCount == 1,
        client.created == List((path, data)),
      )
    },
    test("createDocument maps AlreadyExists to the caller supplied conflict") {
      val client = RecordingFirestoreDocumentClient(createResult =
        ZIO.fail(FirestoreClientError.AlreadyExists)
      )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(
        backend
          .runTransaction(
            _.createDocument(
              FirestoreDocumentPath("slugs", "hello-world"),
              Map("articleId" -> articleId.asString),
              EventStoreError.SlugAlreadyReserved,
            )
          )
          .exit
      )(Assertion.fails(Assertion.equalTo(EventStoreError.SlugAlreadyReserved)))
    },
    test("createDocument maps transaction conflicts to the caller supplied conflict") {
      val client = RecordingFirestoreDocumentClient(createResult =
        ZIO.fail(FirestoreClientError.Conflict("transaction contention"))
      )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(
        backend
          .runTransaction(
            _.createDocument(
              FirestoreDocumentPath("slugs", "hello-world"),
              Map("articleId" -> articleId.asString),
              EventStoreError.SlugAlreadyReserved,
            )
          )
          .exit
      )(Assertion.fails(Assertion.equalTo(EventStoreError.SlugAlreadyReserved)))
    },
    test("createDocument maps client unavailability to EventStoreError.Unavailable") {
      val client =
        RecordingFirestoreDocumentClient(createResult =
          ZIO.fail(FirestoreClientError.Unavailable("down"))
        )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(
        backend
          .runTransaction(
            _.createDocument(
              FirestoreDocumentPath("slugs", "hello-world"),
              Map("articleId" -> articleId.asString),
              EventStoreError.SlugAlreadyReserved,
            )
          )
          .exit
      )(Assertion.fails(Assertion.equalTo(EventStoreError.Unavailable("down"))))
    },
    test("createDocument maps permission failures with an observable message") {
      val client =
        RecordingFirestoreDocumentClient(createResult =
          ZIO.fail(FirestoreClientError.PermissionDenied("iam denied"))
        )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(
        backend
          .runTransaction(
            _.createDocument(
              FirestoreDocumentPath("slugs", "hello-world"),
              Map("articleId" -> articleId.asString),
              EventStoreError.SlugAlreadyReserved,
            )
          )
          .exit
      )(
        Assertion.fails(
          Assertion.equalTo(EventStoreError.Unavailable("firestore permission denied: iam denied"))
        )
      )
    },
    test("createDocument maps invalid request failures with an observable message") {
      val client =
        RecordingFirestoreDocumentClient(createResult =
          ZIO.fail(FirestoreClientError.InvalidArgument("bad request"))
        )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(
        backend
          .runTransaction(
            _.createDocument(
              FirestoreDocumentPath("slugs", "hello-world"),
              Map("articleId" -> articleId.asString),
              EventStoreError.SlugAlreadyReserved,
            )
          )
          .exit
      )(
        Assertion.fails(
          Assertion.equalTo(EventStoreError.Unavailable("invalid Firestore request: bad request"))
        )
      )
    },
    test("loadEvents reads the article events collection and returns events sorted by seq") {
      val client = RecordingFirestoreDocumentClient(
        listedDocuments = Chunk(
          FirestoreDocument(
            "2",
            Map(FirestoreDocumentModel.EventJsonField -> """{"eventType":"ArticlePublished"}""")
          ),
          FirestoreDocument(
            "1",
            Map(FirestoreDocumentModel.EventJsonField -> """{"eventType":"ArticleDrafted"}""")
          ),
        )
      )
      val backend = FirestoreClientEventStoreBackend(client)

      for events <- backend.loadEvents(articleId)
      yield assertTrue(
        client.listed == List(FirestoreDocumentModel.articleEventsCollectionPath(articleId)),
        events == Chunk(
          StoredFirestoreArticleEvent(1L, """{"eventType":"ArticleDrafted"}"""),
          StoredFirestoreArticleEvent(2L, """{"eventType":"ArticlePublished"}"""),
        ),
      )
    },
    test("loadEvents rejects non numeric event document ids") {
      val client = RecordingFirestoreDocumentClient(
        listedDocuments = Chunk(
          FirestoreDocument("not-a-seq", Map(FirestoreDocumentModel.EventJsonField -> "{}"))
        )
      )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(backend.loadEvents(articleId).exit)(
        Assertion.fails(
          Assertion.equalTo(EventStoreError.Unavailable("invalid event seq document id: not-a-seq"))
        )
      )
    },
    test("loadEvents rejects documents missing the JSON payload field") {
      val client = RecordingFirestoreDocumentClient(
        listedDocuments = Chunk(FirestoreDocument("1", Map.empty))
      )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(backend.loadEvents(articleId).exit)(
        Assertion.fails(
          Assertion.equalTo(EventStoreError.Unavailable("missing Firestore field: json"))
        )
      )
    },
    test("loadEvents maps client unavailability to EventStoreError.Unavailable") {
      val client =
        RecordingFirestoreDocumentClient(listResult =
          ZIO.fail(FirestoreClientError.Unavailable("down"))
        )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(backend.loadEvents(articleId).exit)(
        Assertion.fails(Assertion.equalTo(EventStoreError.Unavailable("down")))
      )
    },
    test("loadEvents maps permission failures with an observable message") {
      val client =
        RecordingFirestoreDocumentClient(listResult =
          ZIO.fail(FirestoreClientError.PermissionDenied("iam denied"))
        )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(backend.loadEvents(articleId).exit)(
        Assertion.fails(
          Assertion.equalTo(EventStoreError.Unavailable("firestore permission denied: iam denied"))
        )
      )
    },
    test("loadEvents maps invalid request failures with an observable message") {
      val client =
        RecordingFirestoreDocumentClient(listResult =
          ZIO.fail(FirestoreClientError.InvalidArgument("bad request"))
        )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(backend.loadEvents(articleId).exit)(
        Assertion.fails(
          Assertion.equalTo(EventStoreError.Unavailable("invalid Firestore request: bad request"))
        )
      )
    },
    test("loadEvents maps unexpected AlreadyExists to Unavailable") {
      val client =
        RecordingFirestoreDocumentClient(listResult = ZIO.fail(FirestoreClientError.AlreadyExists))
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(backend.loadEvents(articleId).exit)(
        Assertion.fails(
          Assertion.equalTo(EventStoreError.Unavailable("unexpected Firestore create conflict"))
        )
      )
    },
    test("loadEvents maps unexpected transaction conflicts to Unavailable") {
      val client =
        RecordingFirestoreDocumentClient(listResult =
          ZIO.fail(FirestoreClientError.Conflict("transaction contention"))
        )
      val backend = FirestoreClientEventStoreBackend(client)

      assertZIO(backend.loadEvents(articleId).exit)(
        Assertion.fails(
          Assertion.equalTo(
            EventStoreError.Unavailable(
              "unexpected Firestore transaction conflict: transaction contention"
            )
          )
        )
      )
    },
  )

private final class RecordingFirestoreDocumentClient(
  val createResult: IO[FirestoreClientError, Unit] = ZIO.unit,
  listedDocuments: Chunk[FirestoreDocument] = Chunk.empty,
  listResult: IO[FirestoreClientError, Unit] = ZIO.unit,
) extends FirestoreDocumentClient:
  var transactionCount: Int = 0
  var created: List[(FirestoreDocumentPath, Map[String, String])] = Nil
  var listed: List[FirestoreDocumentPath] = Nil

  def transaction[A](
    effect: FirestoreDocumentTransaction => IO[EventStoreError, A]
  ): IO[EventStoreError, A] =
    ZIO.succeed {
      transactionCount = transactionCount + 1
    } *> effect(RecordingFirestoreDocumentTransaction(this))

  def listDocuments(
    path: FirestoreDocumentPath
  ): IO[FirestoreClientError, Chunk[FirestoreDocument]] =
    ZIO.succeed {
      listed = listed :+ path
    } *> listResult *> ZIO.succeed(listedDocuments)

private final class RecordingFirestoreDocumentTransaction(client: RecordingFirestoreDocumentClient)
    extends FirestoreDocumentTransaction:

  def create(
    path: FirestoreDocumentPath,
    data: Map[String, String]
  ): IO[FirestoreClientError, Unit] =
    client.createResult *> ZIO.succeed {
      client.created = client.created :+ (path, data)
    }
