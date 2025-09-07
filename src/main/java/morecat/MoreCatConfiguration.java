package morecat;

import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.postgresql.PostgreSQLJPAFraction;

public class MoreCatConfiguration {

  private final Container container;

  MoreCatConfiguration(Container container) {
    this.container = container;
  }

  DatasourcesFraction datasourcesFraction(String datasourceName) {
    return new DatasourcesFraction()
        .jdbcDriver("postgresql", (d) -> d
            .driverClassName("org.postgresql.Driver")
            .xaDatasourceClass("org.postgresql.xa.PGXADataSource")
            .driverModuleName("org.postgresql"))
        .dataSource(datasourceName, (ds) -> ds
            .driverName("postgresql")
            .connectionUrl(DB.url())
            .userName(DB.user())
            .password(DB.password()));
  }

  PostgreSQLJPAFraction jpaFraction(String datasourceName) {
    return new PostgreSQLJPAFraction()
        .inhibitDefaultDatasource()
        .defaultDatasource("jboss/datasources/" + datasourceName);
  }

  private static class DB {

    private static String url() {
      return "jdbc:postgresql://" + host() + ":" + port() + "/morecat";
    }

    private static String host() {
      String host = "localhost";

      if (System.getenv("DB_PORT_5432_TCP_ADDR") != null) {
        host = System.getenv("DB_PORT_5432_TCP_ADDR");
      }
      if (System.getProperty("swarm.morecat.db.host") != null) {
        host = System.getProperty("swarm.morecat.db.host");
      }

      return host;
    }

    private static String port() {
      String port = "5432";

      if (System.getenv("DB_PORT_5432_TCP_PORT") != null) {
        port = System.getenv("DB_PORT_5432_TCP_PORT");
      }
      if (System.getProperty("swarm.morecat.db.port") != null) {
        port = System.getProperty("swarm.morecat.db.port");
      }

      return port;
    }

    private static String user() {
      String user = "";

      if (System.getenv("DB_ENV_POSTGRES_USER") != null) {
        user = System.getenv("DB_ENV_POSTGRES_USER");
      }
      if (System.getProperty("swarm.morecat.db.user") != null) {
        user = System.getProperty("swarm.morecat.db.user");
      }

      return user;
    }

    private static String password() {
      String password = "";

      if (System.getenv("DB_ENV_POSTGRES_PASSWORD") != null) {
        password = System.getenv("DB_ENV_POSTGRES_PASSWORD");
      }
      if (System.getProperty("swarm.morecat.db.password") != null) {
        password = System.getProperty("swarm.morecat.db.password");
      }

      return password;
    }
  }

}
