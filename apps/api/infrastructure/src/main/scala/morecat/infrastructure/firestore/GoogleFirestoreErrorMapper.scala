package morecat.infrastructure.firestore

import com.google.cloud.firestore.FirestoreException
import io.grpc.{Status, StatusRuntimeException}

import java.util.concurrent.{CompletionException, ExecutionException}

object GoogleFirestoreErrorMapper:
  def abortedCause(error: Throwable): Option[Throwable] =
    unwrap(error) match
      case firestoreError: FirestoreException
          if firestoreError.getStatus.getCode == Status.Code.ABORTED =>
        Some(firestoreError)
      case grpcError: StatusRuntimeException
          if grpcError.getStatus.getCode == Status.Code.ABORTED =>
        Some(grpcError)
      case _ =>
        None

  def toClientError(error: Throwable): FirestoreClientError =
    unwrap(error) match
      case firestoreError: FirestoreException =>
        toClientError(firestoreError.getStatus.getCode, messageOf(firestoreError))
      case grpcError: StatusRuntimeException =>
        toClientError(grpcError.getStatus.getCode, messageOf(grpcError))
      case other =>
        throw other

  private def unwrap(error: Throwable): Throwable =
    error match
      case wrapped: ExecutionException if wrapped.getCause != null =>
        unwrap(wrapped.getCause)
      case wrapped: CompletionException if wrapped.getCause != null =>
        unwrap(wrapped.getCause)
      case other =>
        other

  private def toClientError(
    code: Status.Code,
    message: String,
  ): FirestoreClientError =
    code match
      case Status.Code.ALREADY_EXISTS =>
        FirestoreClientError.AlreadyExists
      case Status.Code.ABORTED =>
        FirestoreClientError.Conflict(message)
      case Status.Code.FAILED_PRECONDITION =>
        FirestoreClientError.FailedPrecondition(message)
      case Status.Code.PERMISSION_DENIED =>
        FirestoreClientError.PermissionDenied(message)
      case Status.Code.INVALID_ARGUMENT =>
        FirestoreClientError.InvalidArgument(message)
      case _ =>
        FirestoreClientError.Unavailable(message)

  private def messageOf(error: Throwable): String =
    Option(error.getMessage).getOrElse(error.toString)
