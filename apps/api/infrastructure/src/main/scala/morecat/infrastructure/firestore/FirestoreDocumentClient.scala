package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import zio.*

final case class FirestoreDocument(id: String, data: Map[String, String])

enum FirestoreClientError:
  case AlreadyExists
  case Unavailable(message: String)

trait FirestoreDocumentClient:
  def transaction[A](
    effect: FirestoreDocumentClient => IO[EventStoreError, A]
  ): IO[EventStoreError, A]

  def create(path: FirestoreDocumentPath, data: Map[String, String]): IO[FirestoreClientError, Unit]

  def listDocuments(
    path: FirestoreDocumentPath
  ): IO[FirestoreClientError, Chunk[FirestoreDocument]]
