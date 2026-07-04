package morecat.infrastructure.json

import morecat.domain.*
import zio.test.*

object ArticleEventJsonCodecSpec extends ZIOSpecDefault:

  def spec = suite("ArticleEventJsonCodec")(
    test("round-trips ArticleDrafted without leaking JSON concerns into domain") {
      val event =
        ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")

      assertTrue(ArticleEventJsonCodec.decode(ArticleEventJsonCodec.encode(event)) == Right(event))
    },
    test("omits non-applicable fields when encoding ArticleDrafted") {
      val event =
        ArticleDrafted(Slug.applyUnsafe("hello-world"), Title.applyUnsafe("Hello"), "body")

      assertTrue(!ArticleEventJsonCodec.encode(event).contains("publishedAt"))
    },
    test("round-trips ArticlePublished") {
      val event = ArticlePublished(publishedAt = 999L)

      assertTrue(ArticleEventJsonCodec.decode(ArticleEventJsonCodec.encode(event)) == Right(event))
    },
    test("omits draft-only fields when encoding ArticlePublished") {
      val event = ArticlePublished(publishedAt = 999L)

      assertTrue(
        !ArticleEventJsonCodec.encode(event).contains("slug"),
        !ArticleEventJsonCodec.encode(event).contains("title"),
        !ArticleEventJsonCodec.encode(event).contains("body"),
      )
    },
    test("rejects unknown JSON fields") {
      val json =
        """{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"hello-world","title":"Hello","body":"body","extra":true}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects schemaVersion mismatch") {
      val json =
        """{"eventType":"ArticleDrafted","schemaVersion":2,"slug":"hello-world","title":"Hello","body":"body"}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects unsupported eventType") {
      val json =
        """{"eventType":"ArticleArchived","schemaVersion":1}"""

      assertTrue(
        ArticleEventJsonCodec.decode(json) == Left("unsupported eventType: ArticleArchived")
      )
    },
    test("rejects invalid slug at the decode boundary") {
      val json =
        """{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"../bad","title":"Hello","body":"body"}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects ArticleDrafted when a required field is missing") {
      val json =
        """{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"hello-world","title":"Hello"}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects ArticleDrafted when a required field is null") {
      val json =
        """{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"hello-world","title":"Hello","body":null}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects ArticlePublished when publishedAt is missing") {
      val json =
        """{"eventType":"ArticlePublished","schemaVersion":1}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects ArticlePublished when publishedAt is null") {
      val json =
        """{"eventType":"ArticlePublished","schemaVersion":1,"publishedAt":null}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects ArticlePublished when draft-only fields are present") {
      val json =
        """{"eventType":"ArticlePublished","schemaVersion":1,"slug":"hello-world","publishedAt":999}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects ArticlePublished when draft-only fields are null") {
      val json =
        """{"eventType":"ArticlePublished","schemaVersion":1,"slug":null,"title":null,"body":null,"publishedAt":999}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
    test("rejects ArticleDrafted when publishedAt is null") {
      val json =
        """{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"hello-world","title":"Hello","body":"body","publishedAt":null}"""

      assertTrue(ArticleEventJsonCodec.decode(json).isLeft)
    },
  )
