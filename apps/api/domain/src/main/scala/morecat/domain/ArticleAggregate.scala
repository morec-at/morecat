package morecat.domain

/**
 * Event stream 上の順序付き事実。
 *
 * seq は永続化方式だけの都合ではなく、Article 集約を replay して現在状態を決めるための ドメイン上の順序情報として扱う。
 */
final case class SequencedArticleEvent(seq: Long, event: ArticleEvent)

final case class ArticleAggregate private (
  exists: Boolean,
  currentVersion: Long,
  initialDraft: Option[ArticleDrafted],
  alreadyPublished: Boolean,
):
  def isEmpty: Boolean = !exists

  def hasSameInitialDraft(event: ArticleDrafted): Boolean =
    initialDraft.contains(event)

object ArticleAggregate:
  def from(events: Iterable[SequencedArticleEvent]): ArticleAggregate =
    val sortedEvents = events.toSeq.sortBy(_.seq)

    ArticleAggregate(
      exists = sortedEvents.nonEmpty,
      currentVersion = sortedEvents.map(_.seq).maxOption.getOrElse(0L),
      initialDraft =
        sortedEvents.collectFirst { case SequencedArticleEvent(_, event: ArticleDrafted) =>
          event
        },
      alreadyPublished = sortedEvents.exists(_.event.isInstanceOf[ArticlePublished]),
    )
