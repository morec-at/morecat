package morecat.infrastructure.firestore

import morecat.application.EventStoreError

private[firestore] object FirestoreEventStoreErrorMapper:
  def transaction(error: Throwable): EventStoreError =
    transaction(GoogleFirestoreErrorMapper.toClientError(error))

  def transaction(error: FirestoreClientError): EventStoreError =
    error match
      case FirestoreClientError.AlreadyExists | FirestoreClientError.Conflict(_) =>
        EventStoreError.VersionConflict
      case FirestoreClientError.PermissionDenied(message) =>
        EventStoreError.PermissionDenied(message)
      case FirestoreClientError.InvalidArgument(message) =>
        EventStoreError.InvalidArgument(message)
      case FirestoreClientError.Unavailable(message) =>
        EventStoreError.Unavailable(message)

  def read(error: FirestoreClientError): EventStoreError =
    error match
      case FirestoreClientError.AlreadyExists =>
        EventStoreError.Unavailable("unexpected Firestore create conflict")
      case FirestoreClientError.Conflict(message) =>
        EventStoreError.Unavailable(s"unexpected Firestore transaction conflict: $message")
      case FirestoreClientError.PermissionDenied(message) =>
        EventStoreError.PermissionDenied(message)
      case FirestoreClientError.InvalidArgument(message) =>
        EventStoreError.InvalidArgument(message)
      case FirestoreClientError.Unavailable(message) =>
        EventStoreError.Unavailable(message)

  def create(
    alreadyExistsError: EventStoreError
  )(error: FirestoreClientError): EventStoreError =
    error match
      case FirestoreClientError.AlreadyExists =>
        alreadyExistsError
      case FirestoreClientError.Conflict(_) =>
        EventStoreError.VersionConflict
      case FirestoreClientError.PermissionDenied(message) =>
        EventStoreError.PermissionDenied(message)
      case FirestoreClientError.InvalidArgument(message) =>
        EventStoreError.InvalidArgument(message)
      case FirestoreClientError.Unavailable(message) =>
        EventStoreError.Unavailable(message)
