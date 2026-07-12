package morecat.infrastructure.http

import morecat.application.*
import sttp.model.StatusCode
import sttp.tapir.Schema
import sttp.tapir.json.zio.*
import sttp.tapir.ztapir.*
import zio.*
import zio.json.*
import zio.json.ast.Json

final case class CreateArticleRequest(slug: String, title: String, body: String)
    derives JsonEncoder,
      Schema

object CreateArticleRequest:
  private final case class Wire(slug: String, title: String, body: String) derives JsonDecoder
  private val ExpectedFields = Set("slug", "title", "body")

  given JsonDecoder[CreateArticleRequest] = JsonDecoder[Json].mapOrFail {
    case obj: Json.Obj
        if obj.fields.size == ExpectedFields.size &&
          obj.fields.map(_._1).toSet == ExpectedFields =>
      obj.toJson
        .fromJson[Wire]
        .map(wire => CreateArticleRequest(wire.slug, wire.title, wire.body))
    case _: Json.Obj => Left("create article request contains unknown or missing fields")
    case _           => Left("create article request must be a JSON object")
  }

final case class CreateArticleResponse(articleId: String) derives JsonCodec, Schema

final class CreateArticleEndpoint(
  security: CommandSecurity,
  idGenerator: ArticleIdGenerator,
  createDraft: CreateDraftCommand => IO[CommandError, Unit],
):
  val endpoint = security.endpoint.post
    .in("articles")
    .in(jsonBody[CreateArticleRequest])
    .out(statusCode(StatusCode.Created))
    .out(jsonBody[CreateArticleResponse])
    .serverLogic[Any](_ => request => create(request).flatMap(ZIO.fromEither))

  def create(request: CreateArticleRequest): UIO[Either[StatusCode, CreateArticleResponse]] =
    (for
      articleId <- idGenerator.next
      _ <- createDraft(
        CreateDraftCommand(articleId, request.slug, request.title, request.body)
      )
    yield CreateArticleResponse(articleId.asString))
      .mapError(toStatusCode)
      .either

  private def toStatusCode(error: CommandError): StatusCode =
    error match
      case CommandError.InvalidDraft(_)  => StatusCode.BadRequest
      case CommandError.SlugConflict     => StatusCode.Conflict
      case CommandError.VersionConflict  => StatusCode.Conflict
      case CommandError.ArticleNotFound  => StatusCode.NotFound
      case CommandError.StoreFailure     => StatusCode.InternalServerError
      case CommandError.StoreUnavailable => StatusCode.ServiceUnavailable
