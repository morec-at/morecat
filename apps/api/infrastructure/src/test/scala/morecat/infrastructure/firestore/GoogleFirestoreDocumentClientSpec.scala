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
            .mapError(toEventStoreError(EventStoreError.VersionConflict))
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
    test("transaction maps permission failures with an observable message") {
      val operations = RecordingGoogleFirestoreOperations(
        transactionFailure =
          Some(Status.PERMISSION_DENIED.withDescription("iam denied").asRuntimeException())
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.transaction(_ => ZIO.succeed("unused")).exit)(
        Assertion.fails(
          Assertion.equalTo(
            EventStoreError.Unavailable(
              "firestore permission denied: PERMISSION_DENIED: iam denied"
            )
          )
        )
      )
    },
    test("transaction maps invalid request failures with an observable message") {
      val operations = RecordingGoogleFirestoreOperations(
        transactionFailure =
          Some(Status.INVALID_ARGUMENT.withDescription("bad request").asRuntimeException())
      )
      val client = GoogleFirestoreDocumentClient(operations)

      assertZIO(client.transaction(_ => ZIO.succeed("unused")).exit)(
        Assertion.fails(
          Assertion.equalTo(
            EventStoreError.Unavailable("invalid Firestore request: INVALID_ARGUMENT: bad request")
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
            _.create(path, data).mapError(toEventStoreError(EventStoreError.SlugAlreadyReserved))
          )
          .exit
      )(
        Assertion.fails(Assertion.equalTo(EventStoreError.SlugAlreadyReserved))
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
  )

  private def toEventStoreError(
    alreadyExistsError: EventStoreError
  )(error: FirestoreClientError): EventStoreError =
    error match
      case FirestoreClientError.AlreadyExists =>
        alreadyExistsError
      case FirestoreClientError.Conflict(_) =>
        alreadyExistsError
      case FirestoreClientError.PermissionDenied(message) =>
        EventStoreError.Unavailable(s"firestore permission denied: $message")
      case FirestoreClientError.InvalidArgument(message) =>
        EventStoreError.Unavailable(s"invalid Firestore request: $message")
      case FirestoreClientError.Unavailable(message) =>
        EventStoreError.Unavailable(message)

private final class RecordingGoogleFirestoreOperations(
  transactionFailure: Option[Throwable] = None,
  val createFailure: Option[Throwable] = None,
  listFailure: Option[Throwable] = None,
  listResult: Chunk[FirestoreDocument] = Chunk.empty,
) extends GoogleFirestoreOperations:
  var transactionCount: Int = 0
  var committedCount: Int = 0
  var created: List[(FirestoreDocumentPath, Map[String, String])] = Nil
  var listed: List[FirestoreDocumentPath] = Nil

  def runTransaction[A](callback: GoogleFirestoreTransactionOperations => A): A =
    transactionFailure.foreach(throw _)
    transactionCount = transactionCount + 1
    callback(RecordingGoogleFirestoreTransactionOperations(this))

  def listDocuments(path: FirestoreDocumentPath): Chunk[FirestoreDocument] =
    listFailure.foreach(throw _)
    listed = listed :+ path
    listResult

private final class RecordingGoogleFirestoreTransactionOperations(
  operations: RecordingGoogleFirestoreOperations
) extends GoogleFirestoreTransactionOperations:

  def create(path: FirestoreDocumentPath, data: Map[String, String]): Unit =
    operations.createFailure.foreach(throw _)
    operations.created = operations.created :+ (path, data)

  override def commitCreates(): Unit =
    operations.committedCount = operations.committedCount + 1
