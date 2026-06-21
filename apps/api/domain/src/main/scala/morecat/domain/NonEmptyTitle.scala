package morecat.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** 非空タイトル。 */
type NonEmptyTitleConstraint = Not[Empty]
type NonEmptyTitle = String :| NonEmptyTitleConstraint

object NonEmptyTitle:
  def either(value: String): Either[String, NonEmptyTitle] = value.refineEither[NonEmptyTitleConstraint]
  def applyUnsafe(value: String): NonEmptyTitle = value.refineUnsafe[NonEmptyTitleConstraint]
