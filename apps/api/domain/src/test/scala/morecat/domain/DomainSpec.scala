package morecat.domain

import zio.test.*

object DomainSpec extends ZIOSpecDefault:

  /** slug の 1 セグメント（小文字英数字 1..8 文字）。 */
  private val slugSegment: Gen[Any, String] =
    Gen.stringBounded(1, 8)(Gen.oneOf(Gen.char('a', 'z'), Gen.char('0', '9')))

  /** 構造的に正しい slug（セグメントを単一ハイフンで連結）。Iron の制約とは独立に組み立てる。 */
  private val validSlug: Gen[Any, String] =
    Gen.listOfBounded(1, 4)(slugSegment).map(_.mkString("-"))

  /** 既知の不正パターン。いずれも正規表現を確実に破る。 */
  private val invalidSlug: Gen[Any, String] =
    Gen.oneOf(
      Gen.const(""),               // 空
      validSlug.map("-" + _),      // 先頭ハイフン
      validSlug.map(_ + "-"),      // 末尾ハイフン
      validSlug.map(s => s"$s--$s"), // 連続ハイフン
      validSlug.map(_ + "A"),      // 大文字混入
      validSlug.map(_ + " "),      // 空白混入
    )

  def spec = suite("domain")(
    suite("Slug")(
      test("accepts every structurally valid slug and preserves its value") {
        check(validSlug) { s =>
          assertTrue(Slug.either(s) == Right(s))
        }
      },
      test("rejects every malformed slug") {
        check(invalidSlug) { s =>
          assertTrue(Slug.either(s).isLeft)
        }
      },
    ),
    suite("Title")(
      test("accepted iff non-empty") {
        check(Gen.string) { s =>
          assertTrue(Title.either(s).isRight == s.nonEmpty)
        }
      },
    ),
    suite("ArticleId")(
      test("fromString / asString round-trips any string") {
        check(Gen.string) { s =>
          assertTrue(ArticleId.fromString(s).asString == s)
        }
      },
    ),
    suite("ArticleEvent")(
      // schemaVersion は wire 契約の核。現行版を lock して不用意な変更を検出する。
      test("schemaVersion is fixed to the current version (1)") {
        assertTrue(
          ArticleDrafted(Slug.applyUnsafe("a"), Title.applyUnsafe("t"), "body").schemaVersion ==
            ArticleDrafted.CurrentSchemaVersion,
          ArticlePublished(publishedAt = 0L).schemaVersion == ArticlePublished.CurrentSchemaVersion,
          ArticleDrafted.CurrentSchemaVersion == 1,
          ArticlePublished.CurrentSchemaVersion == 1,
        )
      },
    ),
  )
