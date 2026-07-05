package morecat.application

import _root_.morecat.domain.*
import zio.*
import zio.test.Assertion.*
import zio.test.*

object PublishedArticleServiceSpec extends ZIOSpecDefault:

  private val article = PublishedArticle(
    id = ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001"),
    slug = Slug.applyUnsafe("hello-world"),
    title = Title.applyUnsafe("Hello"),
    body = "body",
    publishedAt = 999L,
  )

  private final class StubQuery(result: IO[QueryError, Option[PublishedArticle]])
      extends PublishedArticleQuery:
    var requested: List[Slug] = Nil
    def findBySlug(slug: Slug): IO[QueryError, Option[PublishedArticle]] =
      result <* ZIO.succeed {
        requested = requested :+ slug
      }

  def spec = suite("PublishedArticleService")(
    test("returns the published article for a valid slug") {
      val query = StubQuery(ZIO.succeed(Some(article)))
      val service = PublishedArticleService(query)

      assertZIO(service.getBySlug("hello-world"))(equalTo(article))
    },
    test("returns NotFound when no published projection exists") {
      val query = StubQuery(ZIO.succeed(None))
      val service = PublishedArticleService(query)

      assertZIO(service.getBySlug("hello-world").exit)(
        fails(equalTo(PublishedArticleError.NotFound))
      )
    },
    test("maps query unavailability to QueryUnavailable") {
      val query = StubQuery(ZIO.fail(QueryError.Unavailable("down")))
      val service = PublishedArticleService(query)

      assertZIO(service.getBySlug("hello-world").exit)(
        fails(equalTo(PublishedArticleError.QueryUnavailable)),
      )
    },
    test("rejects invalid slug before querying") {
      val query = StubQuery(ZIO.succeed(Some(article)))
      val service = PublishedArticleService(query)

      for exit <- service.getBySlug("../bad").exit
      yield assertTrue(
        exit == Exit.fail(PublishedArticleError.InvalidSlug),
        query.requested.isEmpty
      )
    },
  )
