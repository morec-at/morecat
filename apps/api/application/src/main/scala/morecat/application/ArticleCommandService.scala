package morecat.application

import morecat.domain.*
import zio.*

final case class CreateDraftCommand(
  articleId: ArticleId,
  slug: String,
  title: String,
  body: String,
)

final case class PublishArticleCommand(
  articleId: ArticleId,
  expectedVersion: Long,
)

enum CommandError:
  case InvalidDraft(errors: Chunk[ArticleDrafted.ValidationError])
  case SlugConflict
  case VersionConflict
  case ArticleNotFound
  case StoreUnavailable

enum PublishResult:
  case Published
  case AlreadyPublished

final class ArticleCommandService(store: ArticleEventStore, clock: ServerClock):

  def createDraft(command: CreateDraftCommand): IO[CommandError, Unit] =
    ZIO
      .fromEither(ArticleDrafted.either(command.slug, command.title, command.body))
      .mapError(errors => CommandError.InvalidDraft(Chunk.fromIterable(errors)))
      .flatMap { event =>
        store.load(command.articleId).mapError(toCommandError).flatMap { events =>
          if events.isEmpty then
            store
              .createDraft(command.articleId, event)
              .mapError(toCommandError)
              .catchAll(recoverCreateDraftConflict(command.articleId, event))
          else if hasSameInitialDraft(events, event) then ZIO.unit
          else ZIO.fail(CommandError.VersionConflict)
        }
      }

  def publish(command: PublishArticleCommand): IO[CommandError, PublishResult] =
    for
      events <- store.load(command.articleId).mapError(toCommandError)
      result <-
        if events.isEmpty then ZIO.fail(CommandError.ArticleNotFound)
        else if alreadyPublished(events) then ZIO.succeed(PublishResult.AlreadyPublished)
        else if currentVersion(events) != command.expectedVersion then
          ZIO.fail(CommandError.VersionConflict)
        else
          for
            publishedAt <- clock.nowMillis
            result <- store
              .append(command.articleId, command.expectedVersion, ArticlePublished(publishedAt))
              .as(PublishResult.Published)
              .mapError(toCommandError)
              .catchAll(recoverPublishConflict(command.articleId))
          yield result
    yield result

  private def recoverCreateDraftConflict(
    articleId: ArticleId,
    event: ArticleDrafted,
  )(error: CommandError): IO[CommandError, Unit] =
    error match
      case CommandError.SlugConflict | CommandError.VersionConflict =>
        store.load(articleId).mapError(toCommandError).flatMap { events =>
          if hasSameInitialDraft(events, event) then ZIO.unit
          else ZIO.fail(error)
        }
      case other => ZIO.fail(other)

  private def recoverPublishConflict(
    articleId: ArticleId,
  )(error: CommandError): IO[CommandError, PublishResult] =
    error match
      case CommandError.VersionConflict =>
        store.load(articleId).mapError(toCommandError).flatMap { events =>
          if alreadyPublished(events) then ZIO.succeed(PublishResult.AlreadyPublished)
          else ZIO.fail(error)
        }
      case other => ZIO.fail(other)

  private def toCommandError(error: EventStoreError): CommandError =
    error match
      case EventStoreError.SlugAlreadyReserved => CommandError.SlugConflict
      case EventStoreError.VersionConflict     => CommandError.VersionConflict
      case EventStoreError.Unavailable(_)      => CommandError.StoreUnavailable

  private def currentVersion(events: Chunk[SequencedArticleEvent]): Long =
    events.map(_.seq).maxOption.getOrElse(0L)

  private def alreadyPublished(events: Chunk[SequencedArticleEvent]): Boolean =
    events.exists(_.event.isInstanceOf[ArticlePublished])

  private def hasSameInitialDraft(
    events: Chunk[SequencedArticleEvent],
    event: ArticleDrafted
  ): Boolean =
    events.minByOption(_.seq).exists(_.event == event)
