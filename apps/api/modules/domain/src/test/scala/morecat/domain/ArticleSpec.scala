package morecat.domain

import io.github.iltotore.iron.refineUnsafe
import morecat.domain.Article.*
import morecat.domain.Article.Command.UpdateTitle
import morecat.domain.Article.Event.*
import zio.test.*

object ArticleSpec extends ZIOSpecDefault {
  def spec = suite("ArticleSpec")(
    suite("updatedTitle") {
      test("not blank") {
        // Given
        val article =
          Public(Title("title".refineUnsafe), Body("body".refineUnsafe))
        // When
        val result =
          article.updateTitle(UpdateTitle(Title("updated title".refineUnsafe)))
        // Then
        assertTrue(
          result == Right(TitleUpdated(Title("updated title".refineUnsafe)))
        )
      }
    },
    suite("publish") {
      test("from private") {
        // Given
        val article =
          Private(Title("title".refineUnsafe), Body("body".refineUnsafe))
        // When
        val result = article.publish
        // Then
        assertTrue(result == Right(Published))
      }
    },
    suite("replay") {
      test("several events") {
        // Given
        val event1 =
          Drafted(Title("title1".refineUnsafe), Body("body1".refineUnsafe))
        val event2 = TitleUpdated(Title("title2".refineUnsafe))
        val event3 = BodyUpdated(Body("body2".refineUnsafe))
        val event4 = Published
        val event5 = Unpublished
        val event6 = BodyUpdated(Body("body3".refineUnsafe))
        val event7 = Published
        val events =
          List(event1, event2, event3, event4, event5, event6, event7)
        // When
        val result = Article.replay(events)
        // Then
        assertTrue(
          result == Public(
            Title("title2".refineUnsafe),
            Body("body3".refineUnsafe)
          )
        )
      }
    }
  )
}
