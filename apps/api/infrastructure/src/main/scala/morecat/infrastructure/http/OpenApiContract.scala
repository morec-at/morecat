package morecat.infrastructure.http

import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

object OpenApiContract:
  def generate: String =
    OpenAPIDocsInterpreter()
      .toOpenAPI(
        List(
          CreateArticleEndpoint.endpoint,
          PublishArticleEndpoint.endpoint,
          GetPublishedArticleEndpoint.endpoint,
        ),
        "morecat API",
        "1.0.0",
      )
      .toYaml
