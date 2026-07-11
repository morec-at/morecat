package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import com.google.cloud.firestore.{Firestore, QueryDocumentSnapshot, Transaction}
import io.grpc.Status
import zio.*

import java.util.concurrent.ExecutionException
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

final class GoogleFirestoreDocumentClient(operations: GoogleFirestoreOperations)
    extends FirestoreDocumentClient:

  def transaction[A](
    effect: FirestoreDocumentTransaction => IO[EventStoreError, A]
  ): IO[EventStoreError, A] =
    ZIO.runtime[Any].flatMap { runtime =>
      ZIO
        .attemptBlocking {
          operations.runTransaction { transaction =>
            Unsafe.unsafe { implicit unsafe =>
              val result =
                try
                  runtime.unsafe
                    .run(effect(GoogleFirestoreDocumentTransaction(transaction)).either)
                    .getOrThrowFiberFailure()
                catch
                  case failure: FiberFailure =>
                    val cause = failure.cause.asInstanceOf[Cause[Throwable]].squash
                    GoogleFirestoreErrorMapper.abortedCause(cause) match
                      case Some(aborted) => throw aborted
                      case None          => throw TransactionCallbackDefect(cause)

              result match
                case Right(_) => transaction.commitCreates()
                case Left(_)  => ()

              result
            }
          }
        }
        .catchAll {
          case TransactionCallbackDefect(cause) => ZIO.die(cause)
          case error                            => ZIO.fail(toEventStoreError(error))
        }
        .flatMap(ZIO.fromEither)
    }

  def listDocuments(
    path: FirestoreDocumentPath
  ): IO[FirestoreClientError, Chunk[FirestoreDocument]] =
    ZIO
      .attemptBlocking(operations.listDocuments(path))
      .mapError(GoogleFirestoreErrorMapper.toClientError)

  private def toEventStoreError(error: Throwable): EventStoreError =
    GoogleFirestoreErrorMapper.toClientError(error) match
      case FirestoreClientError.AlreadyExists =>
        EventStoreError.VersionConflict
      case FirestoreClientError.Conflict(_) =>
        EventStoreError.VersionConflict
      case FirestoreClientError.PermissionDenied(message) =>
        EventStoreError.PermissionDenied(message)
      case FirestoreClientError.InvalidArgument(message) =>
        EventStoreError.InvalidArgument(message)
      case FirestoreClientError.Unavailable(message) =>
        EventStoreError.Unavailable(message)

private final case class TransactionCallbackDefect(cause: Throwable) extends RuntimeException(cause)

private final class GoogleFirestoreDocumentTransaction(
  transaction: GoogleFirestoreTransactionOperations
) extends FirestoreDocumentTransaction:

  def create(
    path: FirestoreDocumentPath,
    data: Map[String, String]
  ): IO[FirestoreClientError, Unit] =
    ZIO
      .attemptBlocking(transaction.create(path, data))
      .catchAll { error =>
        GoogleFirestoreErrorMapper.abortedCause(error) match
          case Some(cause) => ZIO.die(cause)
          case None        => ZIO.fail(GoogleFirestoreErrorMapper.toClientError(error))
      }

trait GoogleFirestoreOperations:
  def runTransaction[A](callback: GoogleFirestoreTransactionOperations => A): A
  def listDocuments(path: FirestoreDocumentPath): Chunk[FirestoreDocument]

trait GoogleFirestoreTransactionOperations:
  def create(path: FirestoreDocumentPath, data: Map[String, String]): Unit
  def commitCreates(): Unit

// $COVERAGE-OFF$
object GoogleFirestoreOperations:
  def fromFirestore(firestore: Firestore): GoogleFirestoreOperations =
    LiveGoogleFirestoreOperations(firestore)

private final class LiveGoogleFirestoreOperations(firestore: Firestore)
    extends GoogleFirestoreOperations:

  def runTransaction[A](callback: GoogleFirestoreTransactionOperations => A): A =
    try
      firestore
        .runTransaction(
          new Transaction.Function[A]:
            def updateCallback(transaction: Transaction): A =
              callback(LiveGoogleFirestoreTransactionOperations(firestore, transaction))
        )
        .get()
    catch
      case wrapped: ExecutionException
          if wrapped.getCause.isInstanceOf[TransactionCallbackDefect] =>
        throw wrapped.getCause

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

  private val stagedCreates =
    mutable.ArrayBuffer.empty[(FirestoreDocumentPath, Map[String, String])]

  def create(path: FirestoreDocumentPath, data: Map[String, String]): Unit =
    val document = firestore.document(path.asString)
    val snapshot = transaction.get(document).get()

    if snapshot.exists() then
      throw Status.ALREADY_EXISTS
        .withDescription(s"Firestore document already exists: ${path.asString}")
        .asRuntimeException()

    stagedCreates += path -> data

  override def commitCreates(): Unit =
    stagedCreates.foreach { case (path, data) =>
      val _ = transaction.create(firestore.document(path.asString), data.asJava)
    }
// $COVERAGE-ON$
