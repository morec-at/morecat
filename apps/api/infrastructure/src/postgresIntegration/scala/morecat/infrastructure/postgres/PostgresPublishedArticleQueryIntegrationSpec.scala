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
      ZIO.acquireRelease(
        ZIO.attemptBlocking {
          val postgres = TestPostgres()
          postgres.start()
          postgres
        }
      )(postgres => ZIO.attemptBlocking(postgres.stop()).orDie).flatMap { postgres =>
        for
          _ <- ZIO.attemptBlocking(
            Flyway
              .configure()
              .dataSource(postgres.getJdbcUrl, postgres.getUsername, postgres.getPassword)
              .load()
              .migrate()
          )
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
    }
  )

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
