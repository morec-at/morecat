package morecat.infrastructure.json

import morecat.domain.*
import zio.test.*

object ArticleEventJsonCodecSpec extends ZIOSpecDefault:

  def spec = suite("ArticleEventJsonCodec")(
    test("round-trips ArticleDrafted without leaking JSON concerns into domain") {
      val event = ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")

      assertTrue(ArticleEventJsonCodec.decode(ArticleEventJsonCodec.encode(event)) == Right(event))
    },
    test("round-trips ArticlePublished") {
      val event = ArticlePublished(publishedAt = 999L)

      assertTrue(ArticleEventJsonCodec.decode(ArticleEventJsonCodec.encode(event)) == Right(event))
    },
    test("rejects unknown JSON fields") {
      val json =
        """{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"hello-world","title":"Hello","body":"body","publishedAt":null,"extra":true}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects schemaVersion mismatch") {
      val json =
        """{"eventType":"ArticleDrafted","schemaVersion":2,"slug":"hello-world","title":"Hello","body":"body","publishedAt":null}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects invalid slug at the decode boundary") {
      val json =
        """{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"../bad","title":"Hello","body":"body","publishedAt":null}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
  )
