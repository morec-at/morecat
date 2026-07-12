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
          documents <- client.listDocuments(FirestoreDocumentPath(collection))
          firstSnapshot  <- ZIO.attemptBlocking(firestore.document(first.asString).get().get())
          secondSnapshot <- ZIO.attemptBlocking(firestore.document(second.asString).get().get())
        yield assertTrue(
          documents.map(document => document.id -> document.data).toMap == Map(
            "first" -> Map("value" -> "one"),
            "second" -> Map("value" -> "two"),
          ),
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
    test("maps one of two concurrent creates to the caller supplied error") {
      withTwoClients { (firstClient, secondClient) =>
        val path = FirestoreDocumentPath(uniqueCollection(), "contended")

        for
          ready <- Ref.make(0)
          start <- Promise.make[Nothing, Unit]
          create = (client: FirestoreDocumentClient) =>
            client.transaction { transaction =>
              for
                readyCount <- ready.updateAndGet(_ + 1)
                _ <- ZIO.when(readyCount == 2)(start.succeed(()))
                _ <- start.await.timeoutFail(
                  EventStoreError.Unavailable("concurrent transaction callback did not start")
                )(10.seconds)
                _ <- transaction
                  .create(path, Map("value" -> "created"))
                  .mapError(
                    FirestoreEventStoreErrorMapper.create(EventStoreError.SlugAlreadyReserved)
                  )
              yield ()
            }
          exits <- ZIO.collectAllPar(List(create(firstClient).exit, create(secondClient).exit))
        yield assertTrue(
          exits.count(_ == Exit.succeed(())) == 1,
          exits.count(_ == Exit.fail(EventStoreError.SlugAlreadyReserved)) == 1,
        )
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
    test("preserves callback defects wrapped by the Firestore SDK") {
      withFirestore { (_, client) =>
        val defect = RuntimeException("callback bug")

        assertZIO(client.transaction(_ => ZIO.die(defect)).exit)(
          Assertion.dies(Assertion.equalTo(defect))
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
    effect: (Firestore, FirestoreDocumentClient) => ZIO[
      Any,
      Throwable | EventStoreError | FirestoreClientError,
      TestResult,
    ]
  ): ZIO[Any, Throwable | EventStoreError | FirestoreClientError, TestResult] =
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

  private def withTwoClients(
    effect: (
      FirestoreDocumentClient,
      FirestoreDocumentClient,
    ) => ZIO[Any, EventStoreError, TestResult]
  ): ZIO[Any, Throwable | EventStoreError, TestResult] =
    ZIO.scoped {
      for
        firstFirestore  <- firestoreResource
        secondFirestore <- firestoreResource
        result <- effect(
          GoogleFirestoreDocumentClient(
            GoogleFirestoreOperations.fromFirestore(firstFirestore)
          ),
          GoogleFirestoreDocumentClient(
            GoogleFirestoreOperations.fromFirestore(secondFirestore)
          ),
        )
      yield result
    }

  private def firestoreResource: ZIO[Scope, Throwable, Firestore] =
    ZIO.acquireRelease(ZIO.attempt(createFirestore()))(firestore =>
      ZIO.attempt(firestore.close()).orDie
    )

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
