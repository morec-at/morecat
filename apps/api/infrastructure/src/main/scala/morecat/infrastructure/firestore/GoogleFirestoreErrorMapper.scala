package morecat.infrastructure.firestore

import io.grpc.{Status, StatusRuntimeException}

import java.util.concurrent.{CompletionException, ExecutionException}

object GoogleFirestoreErrorMapper:
  def toClientError(error: Throwable): FirestoreClientError =
    error match
      case wrapped: ExecutionException if wrapped.getCause != null =>
        toClientError(wrapped.getCause)
      case wrapped: CompletionException if wrapped.getCause != null =>
        toClientError(wrapped.getCause)
      case grpcError: StatusRuntimeException
          if grpcError.getStatus.getCode == Status.Code.ALREADY_EXISTS =>
        FirestoreClientError.AlreadyExists
      case other =>
        FirestoreClientError.Unavailable(Option(other.getMessage).getOrElse(other.toString))
