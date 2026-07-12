package morecat.infrastructure.http

import morecat.application.*
import morecat.domain.ArticleId
import morecat.infrastructure.auth.BearerTokenAuthenticator
import sttp.model.StatusCode
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.*
import zio.http.{Body, Header, MediaType, Request, Response, Routes, Status, URL}
import zio.json.*
import zio.test.*

object PublishArticleEndpointSpec extends ZIOSpecDefault:

  private val rawArticleId = "018f4edc-1f5a-7c4b-aef9-000000000001"
  private val security = CommandSecurity(
    BearerTokenAuthenticator.make("expected-token").toOption.get
  )

  def spec = suite("PublishArticleEndpoint")(
    test("publishes the article with the required expected version") {
      for
        recorded <- Ref.make(Option.empty[PublishArticleCommand])
        endpoint = PublishArticleEndpoint(
          security,
          command => recorded.set(Some(command)).as(PublishResult.Published),
        )
        result <- endpoint.execute(rawArticleId, PublishArticleRequest(expectedVersion = 1L))
        command <- recorded.get
      yield assertTrue(
        result == Right(()),
        command.contains(
          PublishArticleCommand(ArticleId.fromString(rawArticleId), expectedVersion = 1L)
        ),
      )
    },
    test("rejects invalid article IDs and negative versions before publishing") {
      for
        called <- Ref.make(false)
        endpoint = PublishArticleEndpoint(
          security,
          _ => called.set(true).as(PublishResult.Published),
        )
        invalidId <- endpoint.execute("not-a-uuid", PublishArticleRequest(1L))
        wrongVersion <- endpoint.execute(
          "550e8400-e29b-41d4-a716-446655440000",
          PublishArticleRequest(1L),
        )
        negativeVersion <- endpoint.execute(rawArticleId, PublishArticleRequest(-1L))
        wasCalled <- called.get
      yield assertTrue(
        invalidId == Left(StatusCode.BadRequest),
        wrongVersion == Left(StatusCode.BadRequest),
        negativeVersion == Left(StatusCode.BadRequest),
        !wasCalled,
      )
    },
    test("maps command errors to HTTP statuses") {
      val cases = List(
        CommandError.InvalidDraft(Chunk.empty) -> StatusCode.BadRequest,
        CommandError.SlugConflict -> StatusCode.Conflict,
        CommandError.VersionConflict -> StatusCode.Conflict,
        CommandError.ArticleNotFound -> StatusCode.NotFound,
        CommandError.StoreFailure -> StatusCode.InternalServerError,
        CommandError.StoreUnavailable -> StatusCode.ServiceUnavailable,
      )

      ZIO
        .foreach(cases) { case (error, status) =>
          val endpoint = PublishArticleEndpoint(security, _ => ZIO.fail(error))
          assertZIO(endpoint.execute(rawArticleId, PublishArticleRequest(1L)))(
            Assertion.isLeft(Assertion.equalTo(status))
          )
        }
        .map(_.reduce(_ && _))
    },
    test("serves the authenticated publish endpoint as no content") {
      val endpoint = PublishArticleEndpoint(
        security,
        _ => ZIO.succeed(PublishResult.Published),
      )
      val routes: Routes[Any, Response] = ZioHttpInterpreter().toHttp(endpoint.endpoint)
      val request = Request
        .post(
          URL.decode(s"http://localhost/articles/$rawArticleId/publish").toOption.get,
          Body.fromString("""{"expectedVersion":1}"""),
        )
        .addHeader(Header.ContentType(MediaType.application.json))
        .addHeader(Header.Authorization.Bearer("expected-token"))

      assertZIO(routes.runZIO(request).map(_.status))(
        Assertion.equalTo(Status.NoContent)
      )
    },
    test("rejects unknown, missing, and non-object publish requests") {
      assertTrue(
        """{"expectedVersion":1,"force":true}""".fromJson[PublishArticleRequest].isLeft,
        "{}".fromJson[PublishArticleRequest].isLeft,
        "[]".fromJson[PublishArticleRequest].isLeft,
      )
    },
  )
