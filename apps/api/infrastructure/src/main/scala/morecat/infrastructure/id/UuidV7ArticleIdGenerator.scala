package morecat.infrastructure.id

import com.fasterxml.uuid.Generators
import morecat.application.ArticleIdGenerator
import morecat.domain.ArticleId
import zio.*

final class UuidV7ArticleIdGenerator extends ArticleIdGenerator:
  private val generator = Generators.timeBasedEpochGenerator()

  override def next: UIO[ArticleId] =
    ZIO.succeed(ArticleId.fromString(generator.generate().toString))
