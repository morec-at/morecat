package morecat.infrastructure.id

import zio.*
import zio.test.*

import java.util.UUID

object UuidV7ArticleIdGeneratorSpec extends ZIOSpecDefault:

  def spec = suite("UuidV7ArticleIdGenerator")(
    test("generates an RFC 9562 version 7 UUID") {
      val generator = UuidV7ArticleIdGenerator()

      assertZIO(generator.next.map(id => UUID.fromString(id.asString).version()))(
        Assertion.equalTo(7)
      )
    },
    test("generates distinct IDs concurrently") {
      val generator = UuidV7ArticleIdGenerator()

      for ids <- ZIO.collectAllPar(List.fill(100)(generator.next))
      yield assertTrue(ids.map(_.asString).distinct.size == 100)
    },
    test("preserves the canonical UUID string in ArticleId") {
      val generator = UuidV7ArticleIdGenerator()

      for id <- generator.next
      yield assertTrue(UUID.fromString(id.asString).toString == id.asString)
    },
  )
