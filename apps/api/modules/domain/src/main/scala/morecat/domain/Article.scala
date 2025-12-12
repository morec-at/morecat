package morecat.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import morecat.domain.Article.Command.*
import morecat.domain.Article.Event.*
import morecat.domain.Article.Rejection.*
import morecat.domain.Article.*

enum Article {
  case BlankPaper
  case Public(title: Title, body: Body)
  case Private(title: Title, body: Body)

  def draft(command: Draft): Either[Rejection, Drafted] = this match {
    case BlankPaper => Right(Drafted(command.title, command.body))
    case _ => Left(AlreadyInProgress)
  }

  def updateTitle(command: UpdateTitle): Either[Rejection, TitleUpdated] =
    Right(TitleUpdated(command.title))

  def updateBody(command: UpdateBody): Either[Rejection, BodyUpdated] =
    Right(BodyUpdated(command.body))

  def publish: Either[Rejection, Published.type] = this match {
    case BlankPaper | Private(_, _) => Right(Published)
    case Public(_, _) => Left(InvalidStatusChange)
  }

  def unpublish: Either[Rejection, Unpublished.type] = this match {
    case BlankPaper | Private(_, _) => Left(InvalidStatusChange)
    case Public(_, _) => Right(Unpublished)
  }
}
object Article {
  private type TitleConstraint = MinLength[1] & MaxLength[255]
  final case class Title(value: String :| TitleConstraint)
  private type BodyConstraint = MinLength[0]
  final case class Body(value: String :| BodyConstraint)

  enum Command {
    case Draft(title: Title, body: Body)
    case UpdateTitle(title: Title)
    case UpdateBody(body: Body)
  }
  enum Event {
    case Drafted(title: Title, body: Body)
    case TitleUpdated(title: Title)
    case BodyUpdated(body: Body)
    case Published
    case Unpublished
  }
  enum Rejection {
    case AlreadyInProgress
    case InvalidTitle
    case InvalidBody
    case InvalidStatusChange
  }

  def replay(events: List[Event]): Article =
    events.reverse.foldRight(BlankPaper) { (event, article: Article) =>
      event match {
        case Drafted(title, body) =>
          article match {
            case BlankPaper => Private(title, body)
            case Public(title, body) => ???
            case Private(title, body) => ???
          }
        case TitleUpdated(title) =>
          article match {
            case BlankPaper => ???
            case Public(_, body) => Public(title, body)
            case Private(_, body) => Private(title, body)
          }
        case BodyUpdated(body) =>
          article match {
            case BlankPaper => ???
            case Public(title, _) => Public(title, body)
            case Private(title, _) => Private(title, body)
          }
        case Published =>
          article match {
            case BlankPaper => ???
            case Public(title, body) => ???
            case Private(title, body) => Public(title, body)
          }
        case Unpublished =>
          article match {
            case BlankPaper => ???
            case Public(title, body) => Private(title, body)
            case Private(title, body) => ???
          }
      }
    }

}
