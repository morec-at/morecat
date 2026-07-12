package morecat.infrastructure.http

import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.*
import zio.http.*

final class ApiHttpApp(createArticleEndpoint: CreateArticleEndpoint):
  private val endpointRoutes: Routes[Any, Response] =
    ZioHttpInterpreter().toHttp(createArticleEndpoint.endpoint)

  val handler: Handler[Scope, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request](handle)

  def handle(request: Request): ZIO[Scope, Nothing, Response] =
    Handler
      .asChunkBounded(request, ApiHttpApp.MaxRequestBodyBytes)
      .runZIO(())
      .foldZIO(
        _ => ZIO.succeed(Response.status(Status.RequestEntityTooLarge)),
        body => endpointRoutes.runZIO(request.withBody(Body.fromChunk(body))),
      )

object ApiHttpApp:
  val MaxRequestBodyBytes: Int = 512 * 1024
