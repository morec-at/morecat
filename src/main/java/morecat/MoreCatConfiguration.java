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
            .connectionUrl("jdbc:postgresql://" + dbUrl() + "/morecat")
            .userName(dbUser())
            .password(dbPassword()));
  }

  PostgreSQLJPAFraction jpaFraction(String datasourceName) {
    return new PostgreSQLJPAFraction()
        .inhibitDefaultDatasource()
        .defaultDatasource("jboss/datasources/" + datasourceName);
  }

  private String dbUrl() {
    return dbHost() + ":" + dbPort();
  }

  private String dbHost() {
    String dbHost = "localhost";

    if (System.getenv("DB_PORT_5432_TCP_ADDR") != null) {
      dbHost = System.getenv("DB_PORT_5432_TCP_ADDR");
    }
    if (System.getProperty("swarm.morecat.db.host") != null) {
      dbHost = System.getProperty("swarm.morecat.db.host");
    }

    return dbHost;
  }

  private String dbPort() {
    String dbPort = "5432";

    if (System.getenv("DB_PORT_5432_TCP_PORT") != null) {
      dbPort = System.getenv("DB_PORT_5432_TCP_PORT");
    }
    if (System.getProperty("swarm.morecat.db.port") != null) {
      dbPort = System.getProperty("swarm.morecat.db.port");
    }

    return dbPort;
  }


  private String dbUser() {
    String dbUser = "";

    if (System.getenv("DB_ENV_POSTGRES_USER") != null) {
      dbUser = System.getenv("DB_ENV_POSTGRES_USER");
    }
    if (System.getProperty("swarm.morecat.db.user") != null) {
      dbUser = System.getProperty("swarm.morecat.db.user");
    }

    return dbUser;
  }

  private String dbPassword() {
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
