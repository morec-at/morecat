package morecat;

/**
 * @author Yoshimasa Tanabe
 */
public class MoreCatConfiguration {

  private MoreCatConfiguration() {}

  public static String getDBUrl() {
    return getDBHost() + ":" + getDBPort();
  }

  private static String getDBHost() {
    String dbHost = "localhost";

    if (System.getenv("DB_PORT_5432_TCP_ADDR") != null) {
      dbHost = System.getenv("DB_PORT_5432_TCP_ADDR");
    }
    if (System.getProperty("swarm.morecat.db.host") != null) {
      dbHost = System.getProperty("swarm.morecat.db.host");
    }

    return dbHost;
  }

  private static String getDBPort() {
    String dbPort = "5432";

    if (System.getenv("DB_PORT_5432_TCP_PORT") != null) {
      dbPort = System.getenv("DB_PORT_5432_TCP_PORT");
    }
    if (System.getProperty("swarm.morecat.db.port") != null) {
      dbPort = System.getProperty("swarm.morecat.db.port");
    }

    return dbPort;
  }


  public static String getDBUser() {
    String dbUser = "";

    if (System.getenv("DB_ENV_POSTGRES_USER") != null) {
      dbUser = System.getenv("DB_ENV_POSTGRES_USER");
    }
    if (System.getProperty("swarm.morecat.db.user") != null) {
      dbUser = System.getProperty("swarm.morecat.db.user");
    }

    return dbUser;
  }

  public static String getDBPassword() {
    String dbPassword = "";

    if (System.getenv("DB_ENV_POSTGRES_PASSWORD") != null) {
      dbPassword = System.getenv("DB_ENV_POSTGRES_PASSWORD");
    }
    if (System.getProperty("swarm.morecat.db.password") != null) {
      dbPassword = System.getProperty("swarm.morecat.db.password");
    }

    return dbPassword;
  }

}
