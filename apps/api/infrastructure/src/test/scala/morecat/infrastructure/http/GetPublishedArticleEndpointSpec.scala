package morecat.infrastructure.http

import morecat.application.*
import morecat.domain.*
import sttp.model.StatusCode
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.*
import zio.http.{Request, Response, Routes, Status, URL}
import zio.json.*
import zio.test.*

object GetPublishedArticleEndpointSpec extends ZIOSpecDefault:

  private val article = PublishedArticle(
    id = ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001"),
    slug = Slug.applyUnsafe("hello-world"),
    title = Title.applyUnsafe("Hello"),
    body = "body",
    publishedAt = 999L,
  )

  def spec = suite("GetPublishedArticleEndpoint")(
    test("returns the published article for its slug") {
      val endpoint = GetPublishedArticleEndpoint(_ => ZIO.succeed(article))

      assertZIO(endpoint.execute("hello-world"))(
        Assertion.isRight(
          Assertion.equalTo(
            PublishedArticleResponse(
              articleId = "018f4edc-1f5a-7c4b-aef9-000000000001",
              slug = "hello-world",
              title = "Hello",
              body = "body",
              publishedAt = 999L,
            )
          )
        )
      )
    },
    test("maps published article errors to HTTP statuses") {
      val cases = List(
        PublishedArticleError.InvalidSlug -> StatusCode.BadRequest,
        PublishedArticleError.NotFound -> StatusCode.NotFound,
        PublishedArticleError.QueryUnavailable -> StatusCode.ServiceUnavailable,
      )

      ZIO
        .foreach(cases) { case (error, status) =>
          val endpoint = GetPublishedArticleEndpoint(_ => ZIO.fail(error))
          assertZIO(endpoint.execute("hello-world"))(
            Assertion.isLeft(Assertion.equalTo(status))
          )
        }
        .map(_.reduce(_ && _))
    },
    test("serves the public GET endpoint without authentication") {
      val endpoint = GetPublishedArticleEndpoint(_ => ZIO.succeed(article))
      val routes: Routes[Any, Response] = ZioHttpInterpreter().toHttp(endpoint.endpoint)
      val request = Request.get(URL.decode("http://localhost/articles/hello-world").toOption.get)

      for
        response <- routes.runZIO(request)
        body <- response.body.asString
      yield assertTrue(
        response.status == Status.Ok,
        body.fromJson[PublishedArticleResponse] == Right(
          PublishedArticleResponse(
            articleId = "018f4edc-1f5a-7c4b-aef9-000000000001",
            slug = "hello-world",
            title = "Hello",
            body = "body",
            publishedAt = 999L,
          )
        ),
      )
    },
  )
