package morecat.infrastructure.firestore

import com.google.cloud.firestore.FirestoreException
import io.grpc.{Status, StatusRuntimeException}

import java.util.concurrent.{CompletionException, ExecutionException}

object GoogleFirestoreErrorMapper:
  def toClientError(error: Throwable): FirestoreClientError =
    error match
      case wrapped: ExecutionException if wrapped.getCause != null =>
        toClientError(wrapped.getCause)
      case wrapped: CompletionException if wrapped.getCause != null =>
        toClientError(wrapped.getCause)
      case firestoreError: FirestoreException =>
        toClientError(firestoreError.getStatus.getCode, messageOf(firestoreError))
      case grpcError: StatusRuntimeException =>
        toClientError(grpcError.getStatus.getCode, messageOf(grpcError))
      case other =>
        FirestoreClientError.Unavailable(messageOf(other))

  private def toClientError(
    code: Status.Code,
    message: String,
  ): FirestoreClientError =
    code match
      case Status.Code.ALREADY_EXISTS =>
        FirestoreClientError.AlreadyExists
      case Status.Code.ABORTED | Status.Code.FAILED_PRECONDITION =>
        FirestoreClientError.Conflict(message)
      case Status.Code.PERMISSION_DENIED =>
        FirestoreClientError.PermissionDenied(message)
      case Status.Code.INVALID_ARGUMENT =>
        FirestoreClientError.InvalidArgument(message)
      case _ =>
        FirestoreClientError.Unavailable(message)

  private def messageOf(error: Throwable): String =
    Option(error.getMessage).getOrElse(error.toString)
