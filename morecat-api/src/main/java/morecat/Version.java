package morecat;

import java.io.IOException;
import java.util.Properties;

/**
 * @author Yoshimasa Tanabe
 */
public class Version {

  private final static String version;
  private final static String gitCommitId;
  private final static String gitCommitIdShort;

  static {
    String versionString;
    String gitCommitIdString;
    String gitCommitIdShortString;
    versionString = gitCommitIdString = gitCommitIdShortString  = "Unknown";

    try {
      Properties props = new Properties();
      props.load(Version.class.getResourceAsStream("version.properties"));
      versionString = props.getProperty("version");
      gitCommitIdString = props.getProperty("git.commit.id");
      gitCommitIdShortString = props.getProperty("git.commit.id.short");
    } catch (IOException e) {
      e.printStackTrace();
    }
    version = versionString;
    gitCommitId = gitCommitIdString;
    gitCommitIdShort = gitCommitIdShortString;
  }

  private Version() {}

  public static String getVersion() {
    return version;
  }

  public static String getGitCommitId() {
    return gitCommitId;
  }

  public static String getGitCommitIdShort() {
    return gitCommitIdShort;
  }

}
