package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import io.grpc.Status
import zio.*
import zio.test.*

object GoogleFirestoreDocumentClientSpec extends ZIOSpecDefault:

  private val path =
    FirestoreDocumentPath("articles", "018f4edc-1f5a-7c4b-aef9-000000000001", "events", "1")
  private val data = Map(
    FirestoreDocumentModel.EventJsonField -> """{"eventType":"ArticleDrafted"}"""
  )

  def spec = suite("GoogleFirestoreDocumentClient")(
    test("transaction delegates to Firestore operations with a transaction-only handle") {
      val operations = RecordingGoogleFirestoreOperations()
      val client = GoogleFirestoreDocumentClient(operations)

      for result <- client.transaction { tx =>
          tx.create(path, data)
            .mapError(FirestoreEventStoreErrorMapper.create(EventStoreError.VersionConflict))
            .as("created")
        }
      yield assertTrue(
        result == "created",
        operations.transactionCount == 1,
        operations.created == List((path, data)),
        operations.committedCount == 1,
      )
    },
    test("transaction returns application errors from the callback") {
      val operations = RecordingGoogleFirestoreOperations()
      val client = GoogleFirestoreDocumentClient(operations)

      for exit <- client.transaction(_ => ZIO.fail(EventStoreError.VersionConflict)).exit
      yield assertTrue(
        exit == Exit.fail(EventStoreError.VersionConflict),
        operations.committedCount == 0,
      )
    },
    test("transaction callback inherits the current FiberRef values") {
      val operations = RecordingGoogleFirestoreOperations()
      val client = GoogleFirestoreDocumentClient(operations)

      for
        fiberRef <- FiberRef.make("default")
        _ <- fiberRef.set("request-context")
        result <- client.transaction(_ => fiberRef.get)
      yield assertTrue(result == "request-context")
    },
    test("transaction preserves callback defects") {
      val operations = RecordingGoogleFirestoreOperations()
      val client = GoogleFirestoreDocumentClient(operations)
      val defect = RuntimeException("callback bug")

      assertZIO(client.transaction(_ => ZIO.die(defect)).exit)(
        Assertion.dies(Assertion.equalTo(defect))
      )
    },
    test("transaction maps Firestore failures to StoreUnavailable") {
      val operations = RecordingGoogleFirestoreOperations(
        transactionFailure =
          Some(Status.UNAVAILABLE.withDescription("firestore down").asRuntimeException())
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.transaction(_ => ZIO.succeed("unused")).exit)(
        Assertion.fails(
          Assertion.equalTo(EventStoreError.Unavailable("UNAVAILABLE: firestore down"))
        )
      )
    },
    test("transaction preserves unexpected bridge failures as defects") {
      val defect = RuntimeException("bridge bug")
      val operations = RecordingGoogleFirestoreOperations(transactionFailure = Some(defect))
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.transaction(_ => ZIO.succeed("unused")).exit)(
        Assertion.dies(Assertion.equalTo(defect))
      )
    },
    test("transaction maps unexpected ALREADY_EXISTS failures to VersionConflict") {
      val operations = RecordingGoogleFirestoreOperations(
        transactionFailure =
          Some(Status.ALREADY_EXISTS.withDescription("exists").asRuntimeException())
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.transaction(_ => ZIO.succeed("unused")).exit)(
        Assertion.fails(Assertion.equalTo(EventStoreError.VersionConflict))
      )
    },
    test("transaction maps contention failures to VersionConflict") {
      val operations = RecordingGoogleFirestoreOperations(
        transactionFailure =
          Some(Status.ABORTED.withDescription("transaction contention").asRuntimeException())
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.transaction(_ => ZIO.succeed("unused")).exit)(
        Assertion.fails(Assertion.equalTo(EventStoreError.VersionConflict))
      )
    },
    test("transaction preserves non-retryable failed preconditions") {
      val operations = RecordingGoogleFirestoreOperations(
        transactionFailure = Some(
          Status.FAILED_PRECONDITION.withDescription("precondition failed").asRuntimeException()
        )
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.transaction(_ => ZIO.succeed("unused")).exit)(
        Assertion.fails(
          Assertion.equalTo(
            EventStoreError.FailedPrecondition("FAILED_PRECONDITION: precondition failed")
          )
        )
      )
    },
    test("transaction preserves non-retryable permission failures") {
      val operations = RecordingGoogleFirestoreOperations(
        transactionFailure =
          Some(Status.PERMISSION_DENIED.withDescription("iam denied").asRuntimeException())
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.transaction(_ => ZIO.succeed("unused")).exit)(
        Assertion.fails(
          Assertion.equalTo(
            EventStoreError.PermissionDenied("PERMISSION_DENIED: iam denied")
          )
        )
      )
    },
    test("transaction preserves non-retryable invalid arguments") {
      val operations = RecordingGoogleFirestoreOperations(
        transactionFailure =
          Some(Status.INVALID_ARGUMENT.withDescription("bad request").asRuntimeException())
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.transaction(_ => ZIO.succeed("unused")).exit)(
        Assertion.fails(
          Assertion.equalTo(
            EventStoreError.InvalidArgument("INVALID_ARGUMENT: bad request")
          )
        )
      )
    },
    test("transaction create maps ALREADY_EXISTS to AlreadyExists") {
      val operations = RecordingGoogleFirestoreOperations(
        createFailure = Some(Status.ALREADY_EXISTS.withDescription("exists").asRuntimeException())
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(
        client
          .transaction(
            _.create(path, data).mapError(
              FirestoreEventStoreErrorMapper.create(EventStoreError.SlugAlreadyReserved)
            )
          )
          .exit
      )(
        Assertion.fails(Assertion.equalTo(EventStoreError.SlugAlreadyReserved))
      )
    },
    test("transaction create preserves unexpected bridge failures as defects") {
      val defect = RuntimeException("bridge bug")
      val operations = RecordingGoogleFirestoreOperations(createFailure = Some(defect))
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(
        client
          .transaction(
            _.create(path, data)
              .mapError(FirestoreEventStoreErrorMapper.create(EventStoreError.VersionConflict))
          )
          .exit
      )(
        Assertion.dies(Assertion.equalTo(defect))
      )
    },
    test("transaction lets Firestore retry an ABORTED create") {
      val operations = RecordingGoogleFirestoreOperations(retryAbortedCreateOnce = true)
      val client = GoogleFirestoreDocumentClient(operations)

      for result <- client.transaction { tx =>
          tx.create(path, data)
            .mapError(
              FirestoreEventStoreErrorMapper.create(EventStoreError.SlugAlreadyReserved)
            )
            .as("created")
        }
      yield assertTrue(
        result == "created",
        operations.callbackCount == 2,
        operations.createAttemptCount == 2,
        operations.committedCount == 1,
      )
    },
    test("listDocuments delegates and returns Firestore documents") {
      val documents = Chunk(FirestoreDocument("1", data))
      val operations = RecordingGoogleFirestoreOperations(listResult = documents)
      val client = GoogleFirestoreDocumentClient(operations)

      for result <- client.listDocuments(FirestoreDocumentPath("articles", "id", "events"))
      yield assertTrue(
        result == documents,
        operations.listed == List(FirestoreDocumentPath("articles", "id", "events")),
      )
    },
    test("document decoder preserves string fields") {
      val result = GoogleFirestoreDocumentDecoder.decode(
        "1",
        Map("json" -> "payload"),
      )

      assertTrue(result == FirestoreDocument("1", Map("json" -> "payload")))
    },
    test("document decoder rejects non-string fields with type context") {
      assertZIO(
        ZIO
          .attempt(
            GoogleFirestoreDocumentDecoder.decode(
              "1",
              Map("seq" -> Long.box(1L)),
            )
          )
          .mapError(GoogleFirestoreErrorMapper.toClientError)
          .exit
      )(
        Assertion.fails(
          Assertion.equalTo(
            FirestoreClientError.InvalidArgument(
              "INVALID_ARGUMENT: Firestore document 1 field seq must be a string, found java.lang.Long"
            )
          )
        )
      )
    },
    test("document decoder reports null fields without exposing values") {
      assertZIO(
        ZIO
          .attempt(
            GoogleFirestoreDocumentDecoder.decode(
              "1",
              Map("json" -> null),
            )
          )
          .mapError(GoogleFirestoreErrorMapper.toClientError)
          .exit
      )(
        Assertion.fails(
          Assertion.equalTo(
            FirestoreClientError.InvalidArgument(
              "INVALID_ARGUMENT: Firestore document 1 field json must be a string, found null"
            )
          )
        )
      )
    },
    test("listDocuments maps Firestore failures") {
      val operations = RecordingGoogleFirestoreOperations(
        listFailure =
          Some(Status.UNAVAILABLE.withDescription("firestore down").asRuntimeException())
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.listDocuments(FirestoreDocumentPath("articles", "id", "events")).exit)(
        Assertion.fails(
          Assertion.equalTo(FirestoreClientError.Unavailable("UNAVAILABLE: firestore down"))
        )
      )
    },
    test("listDocuments preserves unexpected bridge failures as defects") {
      val defect = RuntimeException("bridge bug")
      val operations = RecordingGoogleFirestoreOperations(listFailure = Some(defect))
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.listDocuments(FirestoreDocumentPath("articles")).exit)(
        Assertion.dies(Assertion.equalTo(defect))
      )
    },
  )

private final class RecordingGoogleFirestoreOperations(
  transactionFailure: Option[Throwable] = None,
  val createFailure: Option[Throwable] = None,
  listFailure: Option[Throwable] = None,
  listResult: Chunk[FirestoreDocument] = Chunk.empty,
  val retryAbortedCreateOnce: Boolean = false,
) extends GoogleFirestoreOperations:
  var transactionCount: Int = 0
  var callbackCount: Int = 0
  var createAttemptCount: Int = 0
  var committedCount: Int = 0
  var created: List[(FirestoreDocumentPath, Map[String, String])] = Nil
  var listed: List[FirestoreDocumentPath] = Nil

  def runTransaction[A](callback: GoogleFirestoreTransactionOperations => A): A =
    transactionFailure.foreach(throw _)
    transactionCount = transactionCount + 1
    callbackCount = callbackCount + 1
    try callback(RecordingGoogleFirestoreTransactionOperations(this))
    catch
      case error: io.grpc.StatusRuntimeException
          if retryAbortedCreateOnce && error.getStatus.getCode == Status.Code.ABORTED =>
        callbackCount = callbackCount + 1
        callback(RecordingGoogleFirestoreTransactionOperations(this))

  def listDocuments(path: FirestoreDocumentPath): Chunk[FirestoreDocument] =
    listFailure.foreach(throw _)
    listed = listed :+ path
    listResult

private final class RecordingGoogleFirestoreTransactionOperations(
  operations: RecordingGoogleFirestoreOperations
) extends GoogleFirestoreTransactionOperations:

  override def create(path: FirestoreDocumentPath, data: Map[String, String]): Unit =
    operations.createAttemptCount = operations.createAttemptCount + 1
    if operations.retryAbortedCreateOnce && operations.createAttemptCount == 1 then
      throw Status.ABORTED.withDescription("transaction contention").asRuntimeException()
    operations.createFailure.foreach(throw _)
    operations.created = operations.created :+ (path, data)

  override def commitCreates(): Unit =
    operations.committedCount = operations.committedCount + 1
