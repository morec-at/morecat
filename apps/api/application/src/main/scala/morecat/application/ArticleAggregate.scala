package morecat.application

import morecat.domain.*
import zio.Chunk

final case class ArticleAggregate private (
  currentVersion: Long,
  initialDraft: Option[ArticleDrafted],
  alreadyPublished: Boolean,
):
  def hasSameInitialDraft(event: ArticleDrafted): Boolean =
    initialDraft.contains(event)

object ArticleAggregate:
  def from(events: Chunk[SequencedArticleEvent]): ArticleAggregate =
    val sortedEvents = events.sortBy(_.seq)

    ArticleAggregate(
      currentVersion = sortedEvents.map(_.seq).maxOption.getOrElse(0L),
      initialDraft =
        sortedEvents.collectFirst { case SequencedArticleEvent(_, event: ArticleDrafted) =>
          event
        },
      alreadyPublished = sortedEvents.exists(_.event.isInstanceOf[ArticlePublished]),
    )
