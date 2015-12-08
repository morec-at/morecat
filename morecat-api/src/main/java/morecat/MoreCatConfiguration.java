package morecat;

/**
 * @author Yoshimasa Tanabe
 */
public class MoreCatConfiguration {

  private MoreCatConfiguration() {}

  public static String getDBHost() {
    String dbHost = "localhost";

    if (System.getenv("DB_PORT_5432_TCP_ADDR") != null) {
      dbHost = System.getenv("DB_PORT_5432_TCP_ADDR");
    }
    if (System.getProperty("morecat.db.host") != null) {
      dbHost = System.getProperty("morecat.db.host");
    }

    return dbHost;
  }

  public static String getDBPort() {
    String dbPort = "5432";

    if (System.getenv("DB_PORT_5432_TCP_PORT") != null) {
      dbPort = System.getenv("DB_PORT_5432_TCP_PORT");
    }
    if (System.getProperty("morecat.db.port") != null) {
      dbPort = System.getProperty("morecat.db.port");
    }

    return dbPort;
  }


  public static String getDBUser() {
    String dbUser = "";

    if (System.getenv("DB_USER") != null) {
      dbUser = System.getenv("DB_USER");
    }
    if (System.getProperty("morecat.db.user") != null) {
      dbUser = System.getProperty("morecat.db.user");
    }

    return dbUser;
  }

  public static String getDBPassword() {
    String dbPassword = "";

    if (System.getenv("DB_ENV_POSTGRES_PASSWORD") != null) {
      dbPassword = System.getenv("DB_ENV_POSTGRES_PASSWORD");
    }
    if (System.getProperty("morecat.db.password") != null) {
      dbPassword = System.getProperty("morecat.db.password");
    }

    return dbPassword;
  }

}
