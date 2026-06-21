package morecat.domain

import java.util.UUID

/** 集約 ID。アプリ採番の UUID（v7 を想定。採番・v7 検証は infrastructure 層の責務）。 */
opaque type ArticleId = UUID

object ArticleId:
  def apply(uuid: UUID): ArticleId = uuid

  def parse(s: String): Either[String, ArticleId] =
    try Right(UUID.fromString(s))
    catch case _: IllegalArgumentException => Left(s"invalid UUID: $s")

  extension (id: ArticleId) def value: UUID = id
