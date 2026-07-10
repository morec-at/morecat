package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import com.google.cloud.firestore.{Firestore, QueryDocumentSnapshot, Transaction}
import zio.*

import scala.jdk.CollectionConverters.*

final class GoogleFirestoreDocumentClient(operations: GoogleFirestoreOperations)
    extends FirestoreDocumentClient:

  def transaction[A](
    effect: FirestoreDocumentTransaction => IO[EventStoreError, A]
  ): IO[EventStoreError, A] =
    ZIO
      .attemptBlocking {
        operations.runTransaction { transaction =>
          Unsafe.unsafe { implicit unsafe =>
            Runtime.default.unsafe
              .run(effect(GoogleFirestoreDocumentTransaction(transaction)).either)
              .getOrThrowFiberFailure()
          }
        }
      }
      .mapError(toEventStoreError)
      .flatMap(ZIO.fromEither)

  def listDocuments(
    path: FirestoreDocumentPath
  ): IO[FirestoreClientError, Chunk[FirestoreDocument]] =
    ZIO
      .attemptBlocking(operations.listDocuments(path))
      .mapError(GoogleFirestoreErrorMapper.toClientError)

  private def toEventStoreError(error: Throwable): EventStoreError =
    GoogleFirestoreErrorMapper.toClientError(error) match
      case FirestoreClientError.AlreadyExists =>
        EventStoreError.Unavailable("unexpected Firestore document conflict")
      case FirestoreClientError.Unavailable(message) =>
        EventStoreError.Unavailable(message)

private final class GoogleFirestoreDocumentTransaction(
  transaction: GoogleFirestoreTransactionOperations
) extends FirestoreDocumentTransaction:

  def create(
    path: FirestoreDocumentPath,
    data: Map[String, String]
  ): IO[FirestoreClientError, Unit] =
    ZIO
      .attemptBlocking(transaction.create(path, data))
      .mapError(GoogleFirestoreErrorMapper.toClientError)

trait GoogleFirestoreOperations:
  def runTransaction[A](callback: GoogleFirestoreTransactionOperations => A): A
  def listDocuments(path: FirestoreDocumentPath): Chunk[FirestoreDocument]

trait GoogleFirestoreTransactionOperations:
  def create(path: FirestoreDocumentPath, data: Map[String, String]): Unit

// $COVERAGE-OFF$
object GoogleFirestoreOperations:
  def fromFirestore(firestore: Firestore): GoogleFirestoreOperations =
    LiveGoogleFirestoreOperations(firestore)

private final class LiveGoogleFirestoreOperations(firestore: Firestore)
    extends GoogleFirestoreOperations:

  def runTransaction[A](callback: GoogleFirestoreTransactionOperations => A): A =
    firestore
      .runTransaction(
        new Transaction.Function[A]:
          def updateCallback(transaction: Transaction): A =
            callback(LiveGoogleFirestoreTransactionOperations(firestore, transaction))
      )
      .get()

  def listDocuments(path: FirestoreDocumentPath): Chunk[FirestoreDocument] =
    Chunk.fromIterable(
      firestore
        .collection(path.asString)
        .get()
        .get()
        .getDocuments
        .asScala
        .map(toDocument)
    )

  private def toDocument(snapshot: QueryDocumentSnapshot): FirestoreDocument =
    FirestoreDocument(
      snapshot.getId,
      snapshot.getData.asScala.collect { case (key, value: String) =>
        key -> value
      }.toMap,
    )

private final class LiveGoogleFirestoreTransactionOperations(
  firestore: Firestore,
  transaction: Transaction,
) extends GoogleFirestoreTransactionOperations:

  def create(path: FirestoreDocumentPath, data: Map[String, String]): Unit =
    val _ = transaction.create(firestore.document(path.asString), data.asJava)
    ()
// $COVERAGE-ON$
