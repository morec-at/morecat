package morecat.infrastructure.postgres

import com.augustnagro.magnum.*
import com.augustnagro.magnum.magzio.TransactorZIO
import morecat.application.*
import morecat.domain.*
import zio.*

private[postgres] final case class PublishedArticleRow(
  articleId: String,
  slug: String,
  title: String,
  body: String,
  publishedAt: Long,
) derives DbCodec

private[postgres] trait PublishedArticleRows:
  def findBySlug(slug: String): Task[Option[PublishedArticleRow]]

final class PostgresPublishedArticleQuery private (
  rows: PublishedArticleRows
) extends PublishedArticleQuery:

  override def findBySlug(slug: Slug): IO[QueryError, Option[PublishedArticle]] =
    rows
      .findBySlug(slug)
      .flatMap(row => ZIO.attempt(row.map(toPublishedArticle)))
      .mapError(error => QueryError.Unavailable(errorMessage(error)))

  private def toPublishedArticle(row: PublishedArticleRow): PublishedArticle =
    PublishedArticle(
      id = ArticleId.fromString(row.articleId),
      slug = Slug.applyUnsafe(row.slug),
      title = Title.applyUnsafe(row.title),
      body = row.body,
      publishedAt = row.publishedAt,
    )

  private def errorMessage(error: Throwable): String =
    Option(error.getMessage).getOrElse(error.toString)

object PostgresPublishedArticleQuery:
  // $COVERAGE-OFF$
  def apply(transactor: TransactorZIO): PostgresPublishedArticleQuery =
    new PostgresPublishedArticleQuery(LivePublishedArticleRows(transactor))
  // $COVERAGE-ON$

  private[postgres] def fromRows(rows: PublishedArticleRows): PostgresPublishedArticleQuery =
    new PostgresPublishedArticleQuery(rows)

// The SQL is exercised against Postgres once the shared integration-test database is added.
// $COVERAGE-OFF$
private final class LivePublishedArticleRows(transactor: TransactorZIO)
    extends PublishedArticleRows:
  override def findBySlug(slug: String): Task[Option[PublishedArticleRow]] =
    transactor.connect:
      sql"""
        SELECT article_id, slug, title, body, published_at
        FROM articles
        WHERE slug = $slug AND status = 'published'
        LIMIT 1
      """.query[PublishedArticleRow].run().headOption
// $COVERAGE-ON$
