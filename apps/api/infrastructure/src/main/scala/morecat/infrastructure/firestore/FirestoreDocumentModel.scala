package morecat.infrastructure.firestore

import morecat.domain.*

final case class FirestoreDocumentPath private (segments: Vector[String]):
  def asString: String =
    segments.mkString("/")

object FirestoreDocumentPath:
  def apply(first: String, rest: String*): FirestoreDocumentPath =
    FirestoreDocumentPath((first +: rest).toVector)

object FirestoreDocumentModel:
  val EventJsonField: String = "json"
  val SlugArticleIdField: String = "articleId"

  def articleEventPath(articleId: ArticleId, seq: Long): FirestoreDocumentPath =
    FirestoreDocumentPath("articles", articleId.asString, "events", seq.toString)

  def articleEventsCollectionPath(articleId: ArticleId): FirestoreDocumentPath =
    FirestoreDocumentPath("articles", articleId.asString, "events")

  def slugReservationPath(slug: Slug): FirestoreDocumentPath =
    FirestoreDocumentPath("slugs", slug.toString)

  def articleEventData(json: String): Map[String, String] =
    Map(EventJsonField -> json)

  def slugReservationData(articleId: ArticleId): Map[String, String] =
    Map(SlugArticleIdField -> articleId.asString)
