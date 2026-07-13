package morecat.infrastructure.postgres

import morecat.application.*
import morecat.domain.*
import zio.*
import zio.test.*

object PostgresPublishedArticleQuerySpec extends ZIOSpecDefault:

  def spec = suite("PostgresPublishedArticleQuery")(
    test("maps a published row to the application model") {
      val rows = new PublishedArticleRows:
        override def findBySlug(slug: String): Task[Option[PublishedArticleRow]] =
          ZIO.succeed(
            Some(
              PublishedArticleRow(
                articleId = "018f4edc-1f5a-7c4b-aef9-000000000001",
                slug = slug,
                title = "Hello",
                body = "body",
                publishedAt = 999L,
              )
            )
          )
      val query = PostgresPublishedArticleQuery.fromRows(rows)

      assertZIO(query.findBySlug(Slug.applyUnsafe("hello-world")))(
        Assertion.isSome(
          Assertion.equalTo(
            PublishedArticle(
              id = ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001"),
              slug = Slug.applyUnsafe("hello-world"),
              title = Title.applyUnsafe("Hello"),
              body = "body",
              publishedAt = 999L,
            )
          )
        )
      )
    },
    test("preserves a missing row") {
      val rows = new PublishedArticleRows:
        override def findBySlug(slug: String): Task[Option[PublishedArticleRow]] =
          ZIO.none
      val query = PostgresPublishedArticleQuery.fromRows(rows)

      assertZIO(query.findBySlug(Slug.applyUnsafe("missing")))(Assertion.isNone)
    },
    test("maps database failures to query unavailability") {
      val rows = new PublishedArticleRows:
        override def findBySlug(slug: String): Task[Option[PublishedArticleRow]] =
          ZIO.fail(RuntimeException("database down"))
      val query = PostgresPublishedArticleQuery.fromRows(rows)

      assertZIO(query.findBySlug(Slug.applyUnsafe("hello-world")).exit)(
        Assertion.fails(Assertion.equalTo(QueryError.Unavailable("database down")))
      )
    },
    test("uses the throwable representation when its message is null") {
      val failure = new RuntimeException(null: String)
      val rows = new PublishedArticleRows:
        override def findBySlug(slug: String): Task[Option[PublishedArticleRow]] =
          ZIO.fail(failure)
      val query = PostgresPublishedArticleQuery.fromRows(rows)

      assertZIO(query.findBySlug(Slug.applyUnsafe("hello-world")).exit)(
        Assertion.fails(Assertion.equalTo(QueryError.Unavailable(failure.toString)))
      )
    },
    test("maps invalid read-model values to query unavailability") {
      val rows = new PublishedArticleRows:
        override def findBySlug(slug: String): Task[Option[PublishedArticleRow]] =
          ZIO.succeed(
            Some(
              PublishedArticleRow(
                articleId = "018f4edc-1f5a-7c4b-aef9-000000000001",
                slug = "INVALID SLUG",
                title = "Hello",
                body = "body",
                publishedAt = 999L,
              )
            )
          )
      val query = PostgresPublishedArticleQuery.fromRows(rows)

      assertZIO(query.findBySlug(Slug.applyUnsafe("hello-world")).exit)(
        Assertion.fails(Assertion.isSubtype[QueryError.Unavailable](Assertion.anything))
      )
    },
  )
