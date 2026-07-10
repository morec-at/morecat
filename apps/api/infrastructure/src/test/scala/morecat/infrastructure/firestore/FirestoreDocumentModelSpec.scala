package morecat.infrastructure.firestore

import morecat.domain.*
import zio.test.*

object FirestoreDocumentModelSpec extends ZIOSpecDefault:

  private val articleId =
    ArticleId.fromString("018f4edc-1f5a-7c4b-aef9-000000000001")
  private val slug =
    Slug.applyUnsafe("hello-world")

  def spec = suite("FirestoreDocumentModel")(
    test("renders document paths as slash-separated Firestore paths") {
      assertTrue(
        FirestoreDocumentPath("articles", articleId.asString, "events", "1").asString ==
          s"articles/${articleId.asString}/events/1"
      )
    },
    test("builds the article event document path from articleId and seq") {
      assertTrue(
        FirestoreDocumentModel.articleEventPath(articleId, seq = 1L) ==
          FirestoreDocumentPath("articles", articleId.asString, "events", "1")
      )
    },
    test("builds the slug reservation document path from slug") {
      assertTrue(
        FirestoreDocumentModel.slugReservationPath(slug) ==
          FirestoreDocumentPath("slugs", "hello-world")
      )
    },
    test("stores article events as a JSON payload field") {
      assertTrue(
        FirestoreDocumentModel.articleEventData("""{"eventType":"ArticlePublished"}""") ==
          Map("json" -> """{"eventType":"ArticlePublished"}""")
      )
    },
    test("stores slug reservations as an articleId field") {
      assertTrue(
        FirestoreDocumentModel.slugReservationData(articleId) ==
          Map("articleId" -> articleId.asString)
      )
    },
  )
