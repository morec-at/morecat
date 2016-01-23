package morecat

import spock.lang.Specification
/**
 * @author Yoshimasa Tanabe
 */
class VersionSpec extends Specification {

  def "return version from properties file"() {
    expect:
    Version.getVersion() == 'testVersion'
    Version.getGitCommitId() == 'testGitCommitId'
    Version.getGitCommitIdShort() == 'testGitCommitIdShort'
  }

}
