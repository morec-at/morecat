package morecat.domain

import java.nio.charset.StandardCharsets

/** Markdown 本文。サイズ上限は UTF-8 bytes で判定する。 */
opaque type ArticleBody = String

object ArticleBody:
  val MaxBytes: Int = 256 * 1024

  enum Error:
    case TooLarge

  def either(value: String): Either[Error, ArticleBody] =
    Either.cond(byteSize(value) <= MaxBytes, value, Error.TooLarge)

  def applyUnsafe(value: String): ArticleBody =
    either(value).fold(error => throw IllegalArgumentException(error.toString), identity)

  def fromStoredEvent(value: String): ArticleBody = value

  extension (body: ArticleBody) def value: String = body

  private def byteSize(value: String): Int =
    value.getBytes(StandardCharsets.UTF_8).length
