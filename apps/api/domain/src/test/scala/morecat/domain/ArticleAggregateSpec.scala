package morecat.domain

import zio.test.*

object ArticleAggregateSpec extends ZIOSpecDefault:

  private val draft =
    ArticleDrafted.applyUnsafe("hello-world", "Hello", "body")

  def spec = suite("ArticleAggregate")(
    test("empty stream has version zero and no state") {
      val aggregate = ArticleAggregate.from(Seq.empty)

      assertTrue(
        aggregate.currentVersion == 0L,
        aggregate.isEmpty,
        aggregate.initialDraft.isEmpty,
        !aggregate.alreadyPublished,
      )
    },
    test("stream existence does not depend on currentVersion") {
      val aggregate = ArticleAggregate.from(
        Seq(SequencedArticleEvent(seq = 0L, event = draft))
      )

      assertTrue(
        aggregate.currentVersion == 0L,
        aggregate.exists,
        !aggregate.isEmpty,
      )
    },
    test("currentVersion is the highest sequenced event") {
      val aggregate = ArticleAggregate.from(
        Seq(
          SequencedArticleEvent(seq = 1L, event = draft),
          SequencedArticleEvent(seq = 3L, event = ArticlePublished(publishedAt = 999L)),
        )
      )

      assertTrue(aggregate.currentVersion == 3L)
    },
    test("initialDraft is taken from the earliest sequenced draft event") {
      val laterDraft = ArticleDrafted.applyUnsafe("hello-world", "Changed", "body")
      val aggregate = ArticleAggregate.from(
        Seq(
          SequencedArticleEvent(seq = 2L, event = laterDraft),
          SequencedArticleEvent(seq = 1L, event = draft),
        )
      )

      assertTrue(
        aggregate.initialDraft.contains(draft),
        aggregate.hasSameInitialDraft(draft),
        !aggregate.hasSameInitialDraft(laterDraft),
      )
    },
    test("alreadyPublished is true once the stream contains ArticlePublished") {
      val aggregate = ArticleAggregate.from(
        Seq(
          SequencedArticleEvent(seq = 1L, event = draft),
          SequencedArticleEvent(seq = 2L, event = ArticlePublished(publishedAt = 999L)),
        )
      )

      assertTrue(aggregate.alreadyPublished)
    },
  )
