package morecat.infrastructure.postgres

import com.augustnagro.magnum.magzio.TransactorZIO
import morecat.domain.Slug
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import zio.*
import zio.test.*

import javax.sql.DataSource

object PostgresPublishedArticleQueryIntegrationSpec extends ZIOSpecDefault:

  private final class TestPostgres
      extends PostgreSQLContainer[TestPostgres]("postgres:17-alpine")

  def spec = suite("PostgresPublishedArticleQuery integration")(
    test("returns published rows and hides drafts after applying the migration") {
      withMigratedPostgres { postgres =>
        for
          _ <- insertArticle(
            postgres,
            articleId = "018f4edc-1f5a-7c4b-aef9-000000000001",
            status = "draft",
            slug = "draft-article",
            publishedAt = None,
          )
          _ <- insertArticle(
            postgres,
            articleId = "018f4edc-1f5a-7c4b-aef9-000000000002",
            status = "published",
            slug = "published-article",
            publishedAt = Some(999L),
          )
          dataSource = postgresDataSource(postgres)
          transactor <- ZIO
            .service[TransactorZIO]
            .provideLayer(ZLayer.succeed[DataSource](dataSource) >>> TransactorZIO.layer)
          query = PostgresPublishedArticleQuery(transactor)
          draft <- query.findBySlug(Slug.applyUnsafe("draft-article"))
          published <- query.findBySlug(Slug.applyUnsafe("published-article"))
        yield assertTrue(
          draft.isEmpty,
          published.exists(article =>
            article.id.asString == "018f4edc-1f5a-7c4b-aef9-000000000002" &&
              article.slug == "published-article" &&
              article.title == "Hello" &&
              article.body == "body" &&
              article.publishedAt == 999L
          ),
        )
      }
    },
    test("rejects duplicate slugs") {
      withMigratedPostgres { postgres =>
        for
          _ <- insertArticle(
            postgres,
            articleId = "018f4edc-1f5a-7c4b-aef9-000000000003",
            status = "draft",
            slug = "duplicate-slug",
            publishedAt = None,
          )
          error <- insertArticle(
            postgres,
            articleId = "018f4edc-1f5a-7c4b-aef9-000000000004",
            status = "draft",
            slug = "duplicate-slug",
            publishedAt = None,
          ).flip
        yield assertTrue(hasSqlState(error, "23505"))
      }
    },
    test("rejects unsupported statuses and inconsistent publication timestamps") {
      withMigratedPostgres { postgres =>
        for
          unsupportedStatus <- insertArticle(
            postgres,
            articleId = "018f4edc-1f5a-7c4b-aef9-000000000005",
            status = "archived",
            slug = "archived-article",
            publishedAt = None,
          ).flip
          inconsistentTimestamp <- insertArticle(
            postgres,
            articleId = "018f4edc-1f5a-7c4b-aef9-000000000006",
            status = "draft",
            slug = "published-draft",
            publishedAt = Some(999L),
          ).flip
        yield assertTrue(
          hasSqlState(unsupportedStatus, "23514"),
          hasSqlState(inconsistentTimestamp, "23514"),
        )
      }
    },
  )

  private def withMigratedPostgres[E, A](
    test: TestPostgres => ZIO[Any, E, A]
  ): ZIO[Any, Throwable | E, A] =
    ZIO.scoped {
      ZIO.acquireRelease(
        ZIO.attemptBlocking {
          val postgres = TestPostgres()
          postgres.start()
          postgres
        }
      )(postgres => ZIO.attemptBlocking(postgres.stop()).orDie).flatMap { postgres =>
        ZIO
          .attemptBlocking(
            Flyway
              .configure()
              .dataSource(postgres.getJdbcUrl, postgres.getUsername, postgres.getPassword)
              .load()
              .migrate()
          )
          .zipRight(test(postgres))
      }
    }

  private def hasSqlState(error: Throwable, expected: String): Boolean =
    error match
      case sqlError: java.sql.SQLException => sqlError.getSQLState == expected
      case _                               => false

  private def insertArticle(
    postgres: TestPostgres,
    articleId: String,
    status: String,
    slug: String,
    publishedAt: Option[Long],
  ): Task[Unit] =
    ZIO.attemptBlocking {
      val connection = postgres.createConnection("")
      try
        val statement = connection.prepareStatement(
          """
            INSERT INTO articles (
              article_id, status, slug, title, body, published_at, last_applied_seq
            ) VALUES (?, ?, ?, 'Hello', 'body', ?, 1)
          """
        )
        try
          statement.setString(1, articleId)
          statement.setString(2, status)
          statement.setString(3, slug)
          publishedAt match
            case Some(value) => statement.setLong(4, value)
            case None        => statement.setNull(4, java.sql.Types.BIGINT)
          statement.executeUpdate()
          ()
        finally statement.close()
      finally connection.close()
    }

  private def postgresDataSource(postgres: TestPostgres): PGSimpleDataSource =
    val dataSource = PGSimpleDataSource()
    dataSource.setURL(postgres.getJdbcUrl)
    dataSource.setUser(postgres.getUsername)
    dataSource.setPassword(postgres.getPassword)
    dataSource
