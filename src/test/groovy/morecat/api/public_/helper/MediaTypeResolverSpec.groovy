package morecat.api.public_.helper

import spock.lang.Specification

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
