package morecat.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** 公開 URL の slug。255 文字以内の小文字英数字とハイフン区切り。 */
type SlugConstraint = Match["^[a-z0-9]+(?:-[a-z0-9]+)*$"] & MaxLength[255]
type Slug = String :| SlugConstraint

object Slug:
  val MaxLength: Int = 255

  /** smart constructor（実行時検証）。 */
  def either(value: String): Either[String, Slug] = value.refineEither[SlugConstraint]
  def applyUnsafe(value: String): Slug = value.refineUnsafe[SlugConstraint]
