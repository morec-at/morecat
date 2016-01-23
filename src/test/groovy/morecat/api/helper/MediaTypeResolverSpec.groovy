package morecat.api.helper

import spock.lang.Specification

/**
 * @author Yoshimasa Tanabe
 */
class MediaTypeResolverSpec extends Specification {

  def "should resolve media type from a file name"() {
    expect:
    MediaTypeResolver.resolve(fileName) == mediaType

    where:
    fileName    | mediaType
    "a.txt"     | "text/plain"
    "b.c.d.png" | "image/png"
    "x.foo"     | "application/octet-stream"
  }
}
