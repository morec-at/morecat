package morecat.infrastructure.http

import zio.test.*

import java.nio.file.{Files, Path}

object OpenApiContractSpec extends ZIOSpecDefault:
  def spec = suite("OpenApiContract")(
    test("documents create, publish, and published article endpoints") {
      val yaml = OpenApiContract.generate

      assertTrue(
        yaml.contains("/articles:"),
        yaml.contains("post:"),
        yaml.contains("/articles/{articleId}/publish:"),
        yaml.contains("/articles/{slug}:"),
        yaml.contains("get:"),
      )
    },
    test("writes the generated contract to the requested path") {
      val directory = Files.createTempDirectory("morecat-openapi")
      val output = directory.resolve("openapi.yaml")

      GenerateOpenApi.main(Array(output.toString))

      assertTrue(
        Files.exists(output),
        Files.readString(output) == OpenApiContract.generate,
      )
    },
    test("keeps the committed UI contract in sync with tapir endpoints") {
      val committedContract = Files.readString(
        Path.of("../ui/openapi.yaml")
      )

      assertTrue(committedContract == OpenApiContract.generate)
    },
  )
