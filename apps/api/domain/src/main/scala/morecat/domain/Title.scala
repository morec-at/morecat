package morecat.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** 記事タイトル（非空）。 */
type TitleConstraint = Not[Empty]
type Title           = String :| TitleConstraint

object Title:
  def either(value: String): Either[String, Title] = value.refineEither[TitleConstraint]
  def applyUnsafe(value: String): Title            = value.refineUnsafe[TitleConstraint]
