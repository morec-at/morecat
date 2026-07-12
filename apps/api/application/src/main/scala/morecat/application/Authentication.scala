package morecat.application

import zio.*

enum AuthenticationError:
  case InvalidCredentials

trait Authenticator:
  def authenticate(bearerToken: String): IO[AuthenticationError, Unit]
