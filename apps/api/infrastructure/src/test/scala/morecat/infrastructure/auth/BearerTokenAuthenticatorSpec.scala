package morecat.infrastructure.auth

import zio.*
import zio.test.*

object BearerTokenAuthenticatorSpec extends ZIOSpecDefault:

  def spec = suite("BearerTokenAuthenticator")(
    test("accepts the configured bearer token") {
      val authenticator = BearerTokenAuthenticator.make("expected-token").toOption.get

      assertZIO(authenticator.authenticate("expected-token").exit)(
        Assertion.succeeds(Assertion.anything)
      )
    },
    test("rejects a different bearer token without exposing it") {
      val authenticator = BearerTokenAuthenticator.make("expected-token").toOption.get

      assertZIO(authenticator.authenticate("presented-secret").exit)(
        Assertion.fails(
          Assertion.equalTo(morecat.application.AuthenticationError.InvalidCredentials)
        )
      )
    },
    test("rejects an empty configured token") {
      assertTrue(
        BearerTokenAuthenticator.make("") == Left(
          BearerTokenAuthenticator.ConfigurationError.EmptyExpectedToken
        )
      )
    }
  )
