package morecat.util

import spock.lang.Specification

/**
 * @author Yoshimasa Tanabe
 */
class StringUtilsSpec extends Specification {

  def "blank or not"() {
    expect:
    StringUtils.isBlank(string) == blank

    where:
    string | blank
    ''     | true
    ' '    | true
    null   | true
    'a'    | false
    'a b'  | false
  }

}
