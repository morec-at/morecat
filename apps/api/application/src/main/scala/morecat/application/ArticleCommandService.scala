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
      _     <- store.createDraft(command.articleId, event).mapError(toCommandError)
    yield ()

  def publish(command: PublishArticleCommand): IO[CommandError, PublishResult] =
    for
      events <- store.load(command.articleId).mapError(toCommandError)
      result <-
        if events.isEmpty then ZIO.fail(CommandError.ArticleNotFound)
        else if events.exists(_.event.isInstanceOf[ArticlePublished]) then ZIO.succeed(PublishResult.AlreadyPublished)
        else
          for
            publishedAt <- clock.nowMillis
            _ <- store
              .append(command.articleId, command.expectedVersion, ArticlePublished(publishedAt))
              .mapError(toCommandError)
          yield PublishResult.Published
    yield result

  private def toCommandError(error: EventStoreError): CommandError =
    error match
      case EventStoreError.SlugAlreadyReserved => CommandError.SlugConflict
      case EventStoreError.VersionConflict    => CommandError.VersionConflict
      case EventStoreError.Unavailable(msg)   => CommandError.StoreUnavailable(msg)
