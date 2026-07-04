package morecat.domain

/**
 * 集約 ID。ドメインにとっては不透明な識別子。 実体の採番・表現（UUIDv7 等）と検証は infrastructure の責務。
 */
opaque type ArticleId = String

object ArticleId:
  /** 文字列表現から構築（検証なし＝表現は infra の関心ごと）。 */
  def fromString(raw: String): ArticleId = raw

  extension (id: ArticleId) def asString: String = id
