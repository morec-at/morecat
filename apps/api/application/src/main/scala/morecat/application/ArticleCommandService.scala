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
  case InvalidSlug
  case InvalidTitle
  case SlugConflict
  case VersionConflict
  case ArticleNotFound
  case StoreUnavailable(message: String)

enum PublishResult:
  case Published
  case AlreadyPublished

final class ArticleCommandService(store: ArticleEventStore, clock: ServerClock):

  def createDraft(command: CreateDraftCommand): IO[CommandError, Unit] =
    for
      slug  <- ZIO.fromEither(Slug.either(command.slug)).mapError(_ => CommandError.InvalidSlug)
      title <- ZIO.fromEither(Title.either(command.title)).mapError(_ => CommandError.InvalidTitle)
      event = ArticleDrafted(slug, title, command.body)
      events <- store.load(command.articleId).mapError(toCommandError)
      _      <-
        if events.isEmpty then
          store
            .createDraft(command.articleId, event)
            .mapError(toCommandError)
            .catchAll(recoverCreateDraftConflict(command.articleId, event))
        else if hasSameInitialDraft(events, event) then ZIO.unit
        else ZIO.fail(CommandError.VersionConflict)
    yield ()

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
            _           <- store
              .append(command.articleId, command.expectedVersion, ArticlePublished(publishedAt))
              .mapError(toCommandError)
              .catchAll(recoverPublishConflict(command.articleId))
          yield PublishResult.Published
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
  )(error: CommandError): IO[CommandError, Unit] =
    error match
      case CommandError.VersionConflict =>
        store.load(articleId).mapError(toCommandError).flatMap { events =>
          if alreadyPublished(events) then ZIO.unit
          else ZIO.fail(error)
        }
      case other => ZIO.fail(other)

  private def toCommandError(error: EventStoreError): CommandError =
    error match
      case EventStoreError.SlugAlreadyReserved => CommandError.SlugConflict
      case EventStoreError.VersionConflict     => CommandError.VersionConflict
      case EventStoreError.Unavailable(msg)    => CommandError.StoreUnavailable(msg)

  private def currentVersion(events: Chunk[SequencedArticleEvent]): Long =
    events.map(_.seq).maxOption.getOrElse(0L)

  private def alreadyPublished(events: Chunk[SequencedArticleEvent]): Boolean =
    events.exists(_.event.isInstanceOf[ArticlePublished])

  private def hasSameInitialDraft(
    events: Chunk[SequencedArticleEvent],
    event: ArticleDrafted
  ): Boolean =
    events.minByOption(_.seq).exists(_.event == event)
