package morecat.bootstrap

import com.google.cloud.firestore.FirestoreOptions
import morecat.application.*
import morecat.infrastructure.auth.BearerTokenAuthenticator
import morecat.infrastructure.firestore.*
import morecat.infrastructure.http.{ApiHttpApp, CommandSecurity, CreateArticleEndpoint}
import morecat.infrastructure.id.UuidV7ArticleIdGenerator
import zio.*
import zio.http.Server

import java.util.concurrent.TimeUnit

// Live resource wiring is verified by compilation and local smoke tests.
// $COVERAGE-OFF$
object Main extends ZIOAppDefault:
  private val BearerTokenEnv = "MORECAT_BEARER_TOKEN"

  override def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for
        token <- System
          .env(BearerTokenEnv)
          .someOrFail(IllegalStateException(s"$BearerTokenEnv is required"))
        authenticator <- ZIO
          .fromEither(BearerTokenAuthenticator.make(token))
          .mapError(error => IllegalArgumentException(error.toString))
        firestore <- ZIO.acquireRelease(
          ZIO.attempt(FirestoreOptions.getDefaultInstance.getService)
        )(service => ZIO.attempt(service.close()).orDie)
        documentClient = GoogleFirestoreDocumentClient(
          GoogleFirestoreOperations.fromFirestore(firestore)
        )
        backend = FirestoreClientEventStoreBackend(documentClient)
        eventStore = FirestoreArticleEventStore(backend)
        clock = new ServerClock:
          override def nowMillis: UIO[Long] = Clock.currentTime(TimeUnit.MILLISECONDS)
        commandService = ArticleCommandService(eventStore, clock)
        security = CommandSecurity(authenticator)
        endpoint = CreateArticleEndpoint(
          security,
          UuidV7ArticleIdGenerator(),
          commandService.createDraft,
        )
        app = ApiHttpApp(endpoint)
        _ <- Server.serve(app.handler.toRoutes).provide(Server.default)
      yield ()
    }
// $COVERAGE-ON$
