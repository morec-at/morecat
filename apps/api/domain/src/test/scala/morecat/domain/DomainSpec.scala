package morecat.domain

import zio.test.*

object DomainSpec extends ZIOSpecDefault:

  private val id = ArticleId(java.util.UUID.fromString("018f0000-0000-7000-8000-000000000000"))

  def spec = suite("domain")(
    suite("value objects")(
      test("Slug accepts valid and rejects invalid") {
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
      test("NonEmptyTitle rejects empty") {
        assertTrue(
          NonEmptyTitle.either("My Post").isRight,
          NonEmptyTitle.either("").isLeft,
        )
      },
    ),
    suite("fold")(
      test("drafted then published yields published") {
        val events = List(
          ArticleDrafted(Slug.applyUnsafe("my-post"), NonEmptyTitle.applyUnsafe("My Post"), "body"),
          ArticlePublished(1000L),
        )
        assertTrue(
          Article.fold(id, events).exists(a =>
            a.status == ArticleStatus.Published
              && a.publishedAt.contains(1000L)
              && a.slug == Slug.applyUnsafe("my-post"),
          ),
        )
      },
      test("publish before draft is ignored") {
        assertTrue(Article.fold(id, List(ArticlePublished(1L))).isEmpty)
      },
      test("re-draft keeps the first draft") {
        val events = List(
          ArticleDrafted(Slug.applyUnsafe("first"), NonEmptyTitle.applyUnsafe("First"), "b1"),
          ArticleDrafted(Slug.applyUnsafe("second"), NonEmptyTitle.applyUnsafe("Second"), "b2"),
        )
        assertTrue(Article.fold(id, events).exists(_.slug == Slug.applyUnsafe("first")))
      },
      test("re-publish is idempotent (no extra state change)") {
        val draft = ArticleDrafted(Slug.applyUnsafe("p"), NonEmptyTitle.applyUnsafe("P"), "b")
        val once  = Article.fold(id, List(draft, ArticlePublished(5L)))
        val twice = Article.fold(id, List(draft, ArticlePublished(5L), ArticlePublished(5L)))
        assertTrue(once == twice)
      },
      test("empty stream yields none") {
        assertTrue(Article.fold(id, Nil).isEmpty)
      },
    ),
  )
