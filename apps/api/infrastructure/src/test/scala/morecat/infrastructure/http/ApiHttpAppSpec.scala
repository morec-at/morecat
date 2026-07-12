package morecat.infrastructure.http

import morecat.application.*
import morecat.domain.ArticleId
import morecat.infrastructure.auth.BearerTokenAuthenticator
import zio.*
import zio.http.*
import zio.stream.ZStream
import zio.test.*

object ApiHttpAppSpec extends ZIOSpecDefault:

  private val generatedId =
    ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001")

  private val idGenerator = new ArticleIdGenerator:
    override def next: UIO[ArticleId] = ZIO.succeed(generatedId)

  private val security = CommandSecurity(
    BearerTokenAuthenticator.make("expected-token").toOption.get
  )

  private val publishEndpoint = PublishArticleEndpoint(
    security,
    _ => ZIO.succeed(PublishResult.Published),
  )

  private def request(body: Body): Request =
    Request
      .post(URL.decode("http://localhost/articles").toOption.get, body)
      .addHeader(Header.ContentType(MediaType.application.json))
      .addHeader(Header.Authorization.Bearer("expected-token"))

  def spec = suite("ApiHttpApp")(
    test("routes a request within the body limit to the create endpoint") {
      val json = """{"slug":"hello-world","title":"Hello","body":"body"}"""

      for
        called <- Ref.make(false)
        endpoint = CreateArticleEndpoint(
          security,
          idGenerator,
          _ => called.set(true),
        )
        app = ApiHttpApp(endpoint, publishEndpoint)
        response <- app.handler.runZIO(request(Body.fromString(json)))
        wasCalled <- called.get
      yield assertTrue(response.status == Status.Created, wasCalled)
    },
    test("rejects an oversized chunked request before calling the command") {
      val oversizedJson =
        s"""{"slug":"hello-world","title":"Hello","body":"${"a" * ApiHttpApp.MaxRequestBodyBytes}"}"""

      for
        called <- Ref.make(false)
        endpoint = CreateArticleEndpoint(
          security,
          idGenerator,
          _ => called.set(true),
        )
        app = ApiHttpApp(endpoint, publishEndpoint)
        response <- app.handle(
          request(Body.fromStreamChunked(ZStream.fromIterable(oversizedJson.getBytes)))
        )
        wasCalled <- called.get
      yield assertTrue(response.status == Status.RequestEntityTooLarge, !wasCalled)
    }
  )
