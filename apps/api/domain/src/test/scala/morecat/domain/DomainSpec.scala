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
    suite("Title")(
      test("rejects empty") {
        assertTrue(
          Title.either("My Post").isRight,
          Title.either("").isLeft,
        )
      },
    ),
  )
