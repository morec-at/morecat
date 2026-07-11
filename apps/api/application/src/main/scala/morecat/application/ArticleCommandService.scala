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
  case StoreFailure
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
          val aggregate = ArticleAggregate.from(events)
          if aggregate.isEmpty then
            store
              .createDraft(command.articleId, event)
              .mapError(toCommandError)
              .catchAll(recoverCreateDraftConflict(command.articleId, event))
          else if aggregate.hasSameInitialDraft(event) then ZIO.unit
          else ZIO.fail(CommandError.VersionConflict)
        }
      }

  def publish(command: PublishArticleCommand): IO[CommandError, PublishResult] =
    for
      events <- store.load(command.articleId).mapError(toCommandError)
      aggregate = ArticleAggregate.from(events)
      result <-
        if aggregate.isEmpty then ZIO.fail(CommandError.ArticleNotFound)
        else if aggregate.alreadyPublished then ZIO.succeed(PublishResult.AlreadyPublished)
        else if aggregate.currentVersion != command.expectedVersion then
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
          if ArticleAggregate.from(events).hasSameInitialDraft(event) then ZIO.unit
          else ZIO.fail(error)
        }
      case other => ZIO.fail(other)

  private def recoverPublishConflict(
    articleId: ArticleId,
  )(error: CommandError): IO[CommandError, PublishResult] =
    error match
      case CommandError.VersionConflict =>
        store.load(articleId).mapError(toCommandError).flatMap { events =>
          if ArticleAggregate.from(events).alreadyPublished then
            ZIO.succeed(PublishResult.AlreadyPublished)
          else ZIO.fail(error)
        }
      case other => ZIO.fail(other)

  private def toCommandError(error: EventStoreError): CommandError =
    error match
      case EventStoreError.SlugAlreadyReserved => CommandError.SlugConflict
      case EventStoreError.VersionConflict     => CommandError.VersionConflict
      case EventStoreError.PermissionDenied(_) => CommandError.StoreFailure
      case EventStoreError.InvalidArgument(_)  => CommandError.StoreFailure
      case EventStoreError.Unavailable(_)      => CommandError.StoreUnavailable
