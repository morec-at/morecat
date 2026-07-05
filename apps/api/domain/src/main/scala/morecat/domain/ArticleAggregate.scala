package morecat.domain

final case class SequencedArticleEvent(seq: Long, event: ArticleEvent)

final case class ArticleAggregate private (
  currentVersion: Long,
  initialDraft: Option[ArticleDrafted],
  alreadyPublished: Boolean,
):
  def hasSameInitialDraft(event: ArticleDrafted): Boolean =
    initialDraft.contains(event)

object ArticleAggregate:
  def from(events: Iterable[SequencedArticleEvent]): ArticleAggregate =
    val sortedEvents = events.toSeq.sortBy(_.seq)

    ArticleAggregate(
      currentVersion = sortedEvents.map(_.seq).maxOption.getOrElse(0L),
      initialDraft =
        sortedEvents.collectFirst { case SequencedArticleEvent(_, event: ArticleDrafted) =>
          event
        },
      alreadyPublished = sortedEvents.exists(_.event.isInstanceOf[ArticlePublished]),
    )
