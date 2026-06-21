package morecat.domain

import zio.test.*

object DomainSpec extends ZIOSpecDefault:

  def spec = suite("domain")(
    suite("Slug")(
      test("accepts valid and rejects invalid") {
        assertTrue(
          Slug.either("hello-world").isRight,
          Slug.either("a1-b2-c3").isRight,
          Slug.either("Hello").isLeft,  // 大文字不可
          Slug.either("a--b").isLeft,   // 連続ハイフン不可
          Slug.either("-abc").isLeft,   // 先頭ハイフン不可
          Slug.either("abc-").isLeft,   // 末尾ハイフン不可
          Slug.either("").isLeft,
        )
      },
    ),
    suite("NonEmptyTitle")(
      test("rejects empty") {
        assertTrue(
          NonEmptyTitle.either("My Post").isRight,
          NonEmptyTitle.either("").isLeft,
        )
      },
    ),
    suite("ArticleId")(
      test("parse accepts valid UUID and rejects invalid") {
        assertTrue(
          ArticleId.parse("018f0000-0000-7000-8000-000000000000").isRight,
          ArticleId.parse("not-a-uuid").isLeft,
        )
      },
    ),
  )
