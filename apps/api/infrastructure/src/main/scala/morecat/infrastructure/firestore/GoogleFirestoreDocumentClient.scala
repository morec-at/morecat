package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import com.google.cloud.firestore.{Firestore, Transaction}
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
          case error => ZIO.fail(FirestoreEventStoreErrorMapper.transaction(error))
        }
        .flatMap(ZIO.fromEither)
    }

  def listDocuments(
    path: FirestoreDocumentPath
  ): IO[FirestoreClientError, Chunk[FirestoreDocument]] =
    ZIO
      .attemptBlocking(operations.listDocuments(path))
      .mapError(GoogleFirestoreErrorMapper.toClientError)

private[firestore] object GoogleFirestoreDocumentDecoder:
  def decode(id: String, data: Map[String, Object]): FirestoreDocument =
    FirestoreDocument(
      id,
      data.map {
        case (key, value: String) => key -> value
        case (key, null)          => throw invalidFieldType(id, key, "null")
        case (key, value)         => throw invalidFieldType(id, key, value.getClass.getName)
      },
    )

  private def invalidFieldType(
    documentId: String,
    field: String,
    actualType: String,
  ): Throwable =
    Status.INVALID_ARGUMENT
      .withDescription(
        s"Firestore document $documentId field $field must be a string, found $actualType"
      )
      .asRuntimeException()

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
        .map(snapshot =>
          GoogleFirestoreDocumentDecoder.decode(snapshot.getId, snapshot.getData.asScala.toMap)
        )
    )

private final class LiveGoogleFirestoreTransactionOperations(
  firestore: Firestore,
  transaction: Transaction,
) extends GoogleFirestoreTransactionOperations:

  private val stagedCreates =
    mutable.ArrayBuffer.empty[(FirestoreDocumentPath, Map[String, String])]

  override def create(path: FirestoreDocumentPath, data: Map[String, String]): Unit =
    val document = firestore.document(path.asString)
    // This read puts the document in the transaction's read set: a concurrent creation
    // makes the commit return ABORTED, so the SDK retries the callback and sees the
    // document as existing at this read. Conflicts therefore always surface here (where
    // callers map AlreadyExists to a domain error) and never at commitCreates().
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
