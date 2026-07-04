package morecat.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** 公開 URL の slug。小文字英数字とハイフン区切り（先頭/末尾・連続ハイフン不可）。 */
type SlugConstraint = Match["^[a-z0-9]+(?:-[a-z0-9]+)*$"]
type Slug = String :| SlugConstraint

object Slug:
  /** smart constructor（実行時検証）。 */
  def either(value: String): Either[String, Slug] = value.refineEither[SlugConstraint]
  def applyUnsafe(value: String): Slug = value.refineUnsafe[SlugConstraint]
