package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import zio.*

final case class FirestoreDocument(id: String, data: Map[String, String])

enum FirestoreClientError:
  case AlreadyExists
  case Conflict(message: String)
  case FailedPrecondition(message: String)
  case PermissionDenied(message: String)
  case InvalidArgument(message: String)
  case Unavailable(message: String)

trait FirestoreDocumentClient:
  def transaction[A](
    effect: FirestoreDocumentTransaction => IO[EventStoreError, A]
  ): IO[EventStoreError, A]

  def listDocuments(
    path: FirestoreDocumentPath
  ): IO[FirestoreClientError, Chunk[FirestoreDocument]]

trait FirestoreDocumentTransaction:
  def create(path: FirestoreDocumentPath, data: Map[String, String]): IO[FirestoreClientError, Unit]
