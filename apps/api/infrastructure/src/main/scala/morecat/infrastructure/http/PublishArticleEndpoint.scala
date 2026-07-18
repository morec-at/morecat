package morecat.infrastructure.http

import morecat.application.*
import morecat.domain.ArticleId
import sttp.model.StatusCode
import sttp.tapir.Schema
import sttp.tapir.json.zio.*
import sttp.tapir.ztapir.*
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.util.UUID

final case class PublishArticleRequest(expectedVersion: Long) derives JsonEncoder, Schema

object PublishArticleRequest:
  private final case class Wire(expectedVersion: Long) derives JsonDecoder
  private val ExpectedField = "expectedVersion"

  given JsonDecoder[PublishArticleRequest] = JsonDecoder[Json].mapOrFail {
    case obj: Json.Obj
        if obj.fields.size == 1 && obj.fields.headOption.exists(_._1 == ExpectedField) =>
      obj.toJson.fromJson[Wire].map(wire => PublishArticleRequest(wire.expectedVersion))
    case _: Json.Obj => Left("publish article request contains unknown or missing fields")
    case _           => Left("publish article request must be a JSON object")
  }

final class PublishArticleEndpoint(
  security: CommandSecurity,
  publish: PublishArticleCommand => IO[CommandError, PublishResult],
):
  val endpoint = PublishArticleEndpoint.endpoint
    .zServerSecurityLogic[Any, Unit](bearerToken =>
      security.authenticate(bearerToken).flatMap(ZIO.fromEither)
    )
    .serverLogic[Any](_ => input => execute(input._1, input._2).flatMap(ZIO.fromEither))

  def execute(
    rawArticleId: String,
    request: PublishArticleRequest,
  ): UIO[Either[StatusCode, Unit]] =
    (for
      articleId <- parseArticleId(rawArticleId)
      _ <- ZIO.unless(request.expectedVersion >= 0)(ZIO.fail(StatusCode.BadRequest))
      _ <- publish(PublishArticleCommand(articleId, request.expectedVersion)).mapError(toStatusCode)
    yield ()).either

  private def parseArticleId(value: String): IO[StatusCode, ArticleId] =
    ZIO
      .attempt(UUID.fromString(value))
      .filterOrFail(uuid => uuid.version() == 7 && uuid.toString == value)(StatusCode.BadRequest)
      .map(uuid => ArticleId.fromString(uuid.toString))
      .mapError(_ => StatusCode.BadRequest)

  private def toStatusCode(error: CommandError): StatusCode =
    error match
      case CommandError.InvalidDraft(_)  => StatusCode.BadRequest
      case CommandError.SlugConflict     => StatusCode.Conflict
      case CommandError.VersionConflict  => StatusCode.Conflict
      case CommandError.ArticleNotFound  => StatusCode.NotFound
      case CommandError.StoreFailure     => StatusCode.InternalServerError
      case CommandError.StoreUnavailable => StatusCode.ServiceUnavailable

object PublishArticleEndpoint:
  val endpoint = CommandSecurity.input.post
    .in("articles" / path[String]("articleId") / "publish")
    .in(jsonBody[PublishArticleRequest])
    .errorOut(
      statusCode
        .description(StatusCode.BadRequest, "Invalid request")
        .description(StatusCode.Unauthorized, "Unauthorized")
        .description(StatusCode.NotFound, "Article not found")
        .description(StatusCode.Conflict, "Conflict")
        .description(StatusCode.InternalServerError, "Internal server error")
        .description(StatusCode.ServiceUnavailable, "Service unavailable")
    )
    .out(statusCode(StatusCode.NoContent))
