package morecat.infrastructure.http

import morecat.application.Authenticator
import sttp.model.StatusCode
import sttp.tapir.ztapir.*
import zio.*

final class CommandSecurity(authenticator: Authenticator):
  val endpoint = CommandSecurity.input
    .errorOut(CommandSecurity.errorOutput)
    .zServerSecurityLogic[Any, Unit](bearerToken =>
      authenticate(bearerToken).flatMap(ZIO.fromEither)
    )

  def authenticate(bearerToken: String): UIO[Either[StatusCode, Unit]] =
    authenticator
      .authenticate(bearerToken)
      .as(Right(()))
      .catchAll(_ => ZIO.succeed(Left(StatusCode.Unauthorized)))

object CommandSecurity:
  val input = sttp.tapir.ztapir.endpoint
    .securityIn(auth.bearer[String]())

  val errorOutput = statusCode.description(StatusCode.Unauthorized, "Unauthorized")
