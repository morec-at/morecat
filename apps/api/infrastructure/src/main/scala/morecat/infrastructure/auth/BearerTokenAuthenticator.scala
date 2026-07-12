package morecat.infrastructure.auth

import morecat.application.{AuthenticationError, Authenticator}
import zio.*

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

final class BearerTokenAuthenticator private (expectedToken: Array[Byte]) extends Authenticator:
  override def authenticate(bearerToken: String): IO[AuthenticationError, Unit] =
    ZIO.cond(
      MessageDigest.isEqual(expectedToken, bearerToken.getBytes(StandardCharsets.UTF_8)),
      (),
      AuthenticationError.InvalidCredentials,
    )

object BearerTokenAuthenticator:
  enum ConfigurationError:
    case EmptyExpectedToken

  def make(expectedToken: String): Either[ConfigurationError, BearerTokenAuthenticator] =
    if expectedToken.isEmpty then Left(ConfigurationError.EmptyExpectedToken)
    else Right(BearerTokenAuthenticator(expectedToken.getBytes(StandardCharsets.UTF_8)))
