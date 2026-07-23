package morecat.infrastructure.http

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object GenerateOpenApi:
  def main(args: Array[String]): Unit = write(Path.of(args.head))

  def write(output: Path): Unit =
    Files.writeString(output, OpenApiContract.generate, StandardCharsets.UTF_8)
    ()
