package morecat.infrastructure.http

import morecat.application.*
import morecat.domain.{ArticleDrafted, ArticleId}
import morecat.infrastructure.auth.BearerTokenAuthenticator
import sttp.model.StatusCode
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.*
import zio.http.{Body, Header, MediaType, Request, Response, Routes, Status, URL}
import zio.json.*
import zio.test.*

object CreateArticleEndpointSpec extends ZIOSpecDefault:

  private val generatedId =
    ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001")

  private val idGenerator = new ArticleIdGenerator:
    override def next: UIO[ArticleId] = ZIO.succeed(generatedId)

  private val security = CommandSecurity(
    BearerTokenAuthenticator.make("expected-token").toOption.get
  )

  private def request(json: String, authenticated: Boolean): Request =
    val base = Request
      .post(URL.decode("http://localhost/articles").toOption.get, Body.fromString(json))
      .addHeader(Header.ContentType(MediaType.application.json))
    if authenticated then base.addHeader(Header.Authorization.Bearer("expected-token"))
    else base

  def spec = suite("CreateArticleEndpoint")(
    test("creates a draft with a generated ID and returns it") {
      for
        recorded <- Ref.make(Option.empty[CreateDraftCommand])
        endpoint = CreateArticleEndpoint(
          security,
          idGenerator,
          command => recorded.set(Some(command)),
        )
        result <- endpoint.create(CreateArticleRequest("hello-world", "Hello", "body"))
        command <- recorded.get
      yield assertTrue(
        result == Right(CreateArticleResponse(generatedId.asString)),
        command.contains(
          CreateDraftCommand(generatedId, "hello-world", "Hello", "body")
        ),
      )
    },
    test("maps command errors to HTTP statuses") {
      val cases = List(
        CommandError.InvalidDraft(Chunk(ArticleDrafted.ValidationError.InvalidSlug)) ->
          StatusCode.BadRequest,
        CommandError.SlugConflict -> StatusCode.Conflict,
        CommandError.VersionConflict -> StatusCode.Conflict,
        CommandError.ArticleNotFound -> StatusCode.NotFound,
        CommandError.StoreFailure -> StatusCode.InternalServerError,
        CommandError.StoreUnavailable -> StatusCode.ServiceUnavailable,
      )

      ZIO
        .foreach(cases) { case (error, expectedStatus) =>
          val endpoint = CreateArticleEndpoint(security, idGenerator, _ => ZIO.fail(error))

          assertZIO(
            endpoint.create(CreateArticleRequest("hello-world", "Hello", "body"))
          )(Assertion.isLeft(Assertion.equalTo(expectedStatus)))
        }
        .map(_.reduce(_ && _))
    },
    test("serves the authenticated create endpoint over HTTP") {
      val endpoint = CreateArticleEndpoint(security, idGenerator, _ => ZIO.unit)
      val routes: Routes[Any, Response] = ZioHttpInterpreter().toHttp(endpoint.endpoint)

      for
        response <- routes.runZIO(
          request("""{"slug":"hello-world","title":"Hello","body":"body"}""", true)
        )
        body <- response.body.asString
      yield assertTrue(
        response.status == Status.Created,
        body.fromJson[CreateArticleResponse] == Right(
          CreateArticleResponse(generatedId.asString)
        ),
      )
    },
    test("rejects a missing bearer token before creating a draft") {
      for
        called <- Ref.make(false)
        endpoint = CreateArticleEndpoint(
          security,
          idGenerator,
          _ => called.set(true),
        )
        routes: Routes[Any, Response] = ZioHttpInterpreter().toHttp(endpoint.endpoint)
        response <- routes.runZIO(
          request("""{"slug":"hello-world","title":"Hello","body":"body"}""", false)
        )
        wasCalled <- called.get
      yield assertTrue(response.status == Status.Unauthorized, !wasCalled)
    },
    test("rejects unknown JSON fields before creating a draft") {
      for
        called <- Ref.make(false)
        endpoint = CreateArticleEndpoint(
          security,
          idGenerator,
          _ => called.set(true),
        )
        routes: Routes[Any, Response] = ZioHttpInterpreter().toHttp(endpoint.endpoint)
        response <- routes.runZIO(
          request(
            """{"slug":"hello-world","title":"Hello","body":"body","admin":true}""",
            true,
          )
        )
        wasCalled <- called.get
      yield assertTrue(response.status == Status.BadRequest, !wasCalled)
    },
    test("rejects a non-object JSON request before creating a draft") {
      for
        called <- Ref.make(false)
        endpoint = CreateArticleEndpoint(
          security,
          idGenerator,
          _ => called.set(true),
        )
        routes: Routes[Any, Response] = ZioHttpInterpreter().toHttp(endpoint.endpoint)
        response <- routes.runZIO(request("[]", true))
        wasCalled <- called.get
      yield assertTrue(response.status == Status.BadRequest, !wasCalled)
    },
  )
