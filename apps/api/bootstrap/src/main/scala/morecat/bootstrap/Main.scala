package morecat.bootstrap

import com.google.cloud.firestore.FirestoreOptions
import com.augustnagro.magnum.magzio.TransactorZIO
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import morecat.application.*
import morecat.infrastructure.auth.BearerTokenAuthenticator
import morecat.infrastructure.firestore.*
import morecat.infrastructure.http.{
  ApiHttpApp,
  CommandSecurity,
  CreateArticleEndpoint,
  GetPublishedArticleEndpoint,
  PublishArticleEndpoint
}
import morecat.infrastructure.id.UuidV7ArticleIdGenerator
import morecat.infrastructure.postgres.PostgresPublishedArticleQuery
import zio.*
import zio.http.Server

import java.util.concurrent.TimeUnit

// Live resource wiring is verified by compilation and local smoke tests.
// $COVERAGE-OFF$
object Main extends ZIOAppDefault:
  private val BearerTokenEnv = "MORECAT_BEARER_TOKEN"
  private val PortEnv = "PORT"
  private val DatabaseUrlEnv = "DATABASE_URL"
  private val DatabaseUserEnv = "DATABASE_USER"
  private val DatabasePasswordEnv = "DATABASE_PASSWORD"

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
        databaseUrl <- requiredEnv(DatabaseUrlEnv)
        databaseUser <- requiredEnv(DatabaseUserEnv)
        databasePassword <- requiredEnv(DatabasePasswordEnv)
        dataSource <- ZIO.acquireRelease(
          ZIO.attempt {
            val config = HikariConfig()
            config.setJdbcUrl(databaseUrl)
            config.setUsername(databaseUser)
            config.setPassword(databasePassword)
            HikariDataSource(config)
          }
        )(dataSource => ZIO.attempt(dataSource.close()).orDie)
        transactor <- ZIO
          .service[TransactorZIO]
          .provideLayer(
            ZLayer.succeed[javax.sql.DataSource](dataSource) >>> TransactorZIO.layer
          )
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
        publishedArticleService = PublishedArticleService(
          PostgresPublishedArticleQuery(transactor)
        )
        getEndpoint = GetPublishedArticleEndpoint(publishedArticleService.getBySlug)
        app = ApiHttpApp(endpoint, publishEndpoint, getEndpoint)
        _ <- Server.serve(app.handler.toRoutes).provide(Server.defaultWithPort(port))
      yield ()
    }

  private def requiredEnv(name: String): IO[Throwable, String] =
    System.env(name).someOrFail(IllegalStateException(s"$name is required"))
// $COVERAGE-ON$
