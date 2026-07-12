package morecat.infrastructure.http

import morecat.infrastructure.auth.BearerTokenAuthenticator
import sttp.model.StatusCode
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.http.{Request, Response, Routes, Status, URL}
import zio.test.*

object CommandSecuritySpec extends ZIOSpecDefault:

  private val authenticator =
    BearerTokenAuthenticator.make("expected-token").toOption.get

  def spec = suite("CommandSecurity")(
    test("accepts the configured bearer token") {
      val security = CommandSecurity(authenticator)

      assertZIO(security.authenticate("expected-token"))(
        Assertion.isRight(Assertion.isUnit)
      )
    },
    test("rejects an invalid bearer token as unauthorized") {
      val security = CommandSecurity(authenticator)

      assertZIO(security.authenticate("presented-secret"))(
        Assertion.isLeft(Assertion.equalTo(StatusCode.Unauthorized))
      )
    },
    test("rejects a missing authorization header as unauthorized") {
      val security = CommandSecurity(authenticator)
      val logic: Unit => Unit => UIO[StatusCode] =
        _ => _ => ZIO.succeed(StatusCode.NoContent)
      val probe = security.endpoint.get
        .in("probe")
        .out(statusCode)
        .serverLogic[Any](logic)
      val routes: Routes[Any, Response] = ZioHttpInterpreter().toHttp(probe)
      val request = Request.get(URL.decode("http://localhost/probe").toOption.get)

      assertZIO(routes.runZIO(request).map(_.status))(
        Assertion.equalTo(Status.Unauthorized)
      )
    },
  )
