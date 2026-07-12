package morecat.bootstrap

import com.google.cloud.firestore.FirestoreOptions
import morecat.application.*
import morecat.infrastructure.auth.BearerTokenAuthenticator
import morecat.infrastructure.firestore.*
import morecat.infrastructure.http.{
  ApiHttpApp,
  CommandSecurity,
  CreateArticleEndpoint,
  PublishArticleEndpoint
}
import morecat.infrastructure.id.UuidV7ArticleIdGenerator
import zio.*
import zio.http.Server

import java.util.concurrent.TimeUnit

// Live resource wiring is verified by compilation and local smoke tests.
// $COVERAGE-OFF$
object Main extends ZIOAppDefault:
  private val BearerTokenEnv = "MORECAT_BEARER_TOKEN"
  private val PortEnv = "PORT"

  override def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for
        token <- System
          .env(BearerTokenEnv)
          .someOrFail(IllegalStateException(s"$BearerTokenEnv is required"))
        authenticator <- ZIO
          .fromEither(BearerTokenAuthenticator.make(token))
          .mapError(error => IllegalArgumentException(error.toString))
        port <- System.env(PortEnv).flatMap {
          case None        => ZIO.succeed(8080)
          case Some(value) =>
            ZIO
              .fromOption(value.toIntOption.filter(port => port > 0 && port <= 65535))
              .orElseFail(IllegalArgumentException(s"$PortEnv must be a valid TCP port"))
        }
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
        publishEndpoint = PublishArticleEndpoint(security, commandService.publish)
        app = ApiHttpApp(endpoint, publishEndpoint)
        _ <- Server.serve(app.handler.toRoutes).provide(Server.defaultWithPort(port))
      yield ()
    }
// $COVERAGE-ON$
