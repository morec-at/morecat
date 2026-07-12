package morecat.infrastructure.http

import morecat.application.Authenticator
import sttp.model.StatusCode
import sttp.tapir.ztapir.*
import zio.*

final class CommandSecurity(authenticator: Authenticator):
  val endpoint = sttp.tapir.ztapir.endpoint
    .securityIn(auth.bearer[String]())
    .errorOut(statusCode)
    .zServerSecurityLogic[Any, Unit](bearerToken =>
      authenticate(bearerToken).flatMap(ZIO.fromEither)
    )

  def authenticate(bearerToken: String): UIO[Either[StatusCode, Unit]] =
    authenticator
      .authenticate(bearerToken)
      .as(Right(()))
      .catchAll(_ => ZIO.succeed(Left(StatusCode.Unauthorized)))
