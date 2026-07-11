package morecat.infrastructure.firestore

import morecat.application.EventStoreError
import com.google.cloud.firestore.{Firestore, FirestoreOptions}
import zio.*
import zio.test.*

import java.util.UUID
import scala.jdk.CollectionConverters.*

object GoogleFirestoreIntegrationSpec extends ZIOSpecDefault:

  def spec = suite("GoogleFirestore integration")(
    test("creates multiple documents in one transaction") {
      withFirestore { (firestore, client) =>
        val collection = uniqueCollection()
        val first = FirestoreDocumentPath(collection, "first")
        val second = FirestoreDocumentPath(collection, "second")

        for
          _ <- client.transaction { transaction =>
            transaction
              .create(first, Map("value" -> "one"))
              .mapError(FirestoreEventStoreErrorMapper.create(EventStoreError.VersionConflict)) *>
              transaction
                .create(second, Map("value" -> "two"))
                .mapError(FirestoreEventStoreErrorMapper.create(EventStoreError.VersionConflict))
          }
          firstSnapshot  <- ZIO.attemptBlocking(firestore.document(first.asString).get().get())
          secondSnapshot <- ZIO.attemptBlocking(firestore.document(second.asString).get().get())
        yield assertTrue(
          firstSnapshot.exists(),
          firstSnapshot.getString("value") == "one",
          secondSnapshot.exists(),
          secondSnapshot.getString("value") == "two",
        )
      }
    },
    test("maps an existing document to the caller supplied error") {
      withFirestore { (firestore, client) =>
        val path = FirestoreDocumentPath(uniqueCollection(), "existing")

        for
          _ <- ZIO.attemptBlocking(
            firestore.document(path.asString).set(Map("value" -> "existing").asJava).get()
          )
          exit <- client
            .transaction(
              _.create(path, Map("value" -> "new"))
                .mapError(
                  FirestoreEventStoreErrorMapper.create(EventStoreError.SlugAlreadyReserved)
                )
            )
            .exit
        yield assert(exit)(Assertion.fails(Assertion.equalTo(EventStoreError.SlugAlreadyReserved)))
      }
    },
    test("does not commit staged creates when the callback returns an application error") {
      withFirestore { (firestore, client) =>
        val path = FirestoreDocumentPath(uniqueCollection(), "rolled-back")

        for
          exit <- client
            .transaction { transaction =>
              transaction
                .create(path, Map("value" -> "staged"))
                .mapError(FirestoreEventStoreErrorMapper.create(EventStoreError.VersionConflict)) *>
                ZIO.fail(EventStoreError.VersionConflict)
            }
            .exit
          snapshot <- ZIO.attemptBlocking(firestore.document(path.asString).get().get())
        yield assertTrue(
          exit == Exit.fail(EventStoreError.VersionConflict),
          !snapshot.exists(),
        )
      }
    },
    test("rejects non-string fields read through the Firestore SDK") {
      withFirestore { (firestore, client) =>
        val collection = uniqueCollection()

        for
          _ <- ZIO.attemptBlocking(
            firestore.collection(collection).document("1").set(Map("seq" -> Long.box(1L)).asJava).get()
          )
          exit <- client.listDocuments(FirestoreDocumentPath(collection)).exit
        yield assert(exit)(
          Assertion.fails(
            Assertion.isSubtype[FirestoreClientError.InvalidArgument](Assertion.anything)
          )
        )
      }
    },
  ) @@ TestAspect.sequential

  private def withFirestore(
    effect: (Firestore, FirestoreDocumentClient) => ZIO[Any, Throwable | EventStoreError, TestResult]
  ): ZIO[Any, Throwable | EventStoreError, TestResult] =
    ZIO.scoped {
      ZIO
        .acquireRelease(ZIO.attempt(createFirestore()))(firestore =>
          ZIO.attempt(firestore.close()).orDie
        )
        .flatMap { firestore =>
          effect(
            firestore,
            GoogleFirestoreDocumentClient(GoogleFirestoreOperations.fromFirestore(firestore)),
          )
        }
    }

  private def createFirestore(): Firestore =
    val emulatorHost = sys.env.getOrElse(
      "FIRESTORE_EMULATOR_HOST",
      throw IllegalStateException("FIRESTORE_EMULATOR_HOST must be set")
    )

    FirestoreOptions
      .newBuilder()
      .setProjectId(sys.env.getOrElse("GCLOUD_PROJECT", "demo-morecat"))
      .setEmulatorHost(emulatorHost)
      .build()
      .getService()

  private def uniqueCollection(): String =
    s"integration-${UUID.randomUUID()}"
