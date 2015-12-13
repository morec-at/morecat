package morecat;

import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;

/**
 * @author Yoshimasa Tanabe
 */
public class MoreCatContainer {

  private static final String DB_URL = MoreCatConfiguration.getDBUrl();
  private static final String DB_USER = MoreCatConfiguration.getDBUser();
  private static final String DB_PASSWORD = MoreCatConfiguration.getDBPassword();

  public static Container newContainer() throws Exception {
    Container container = new Container();

    container.fraction(new DatasourcesFraction()
      .jdbcDriver("org.postgresql", (d) -> {
        d.driverDatasourceClassName("org.postgresql.Driver");
        d.xaDatasourceClass("org.postgresql.xa.PGXADataSource");
        d.driverModuleName("org.postgresql");
      })
      .dataSource("morecatDS", (ds) -> {
        ds.driverName("org.postgresql");
        ds.connectionUrl("jdbc:postgresql://" + DB_URL + "/morecat");
        ds.userName(DB_USER);
        ds.password(DB_PASSWORD);
      })
    );

    container.fraction(new JPAFraction()
      .inhibitDefaultDatasource()
      .defaultDatasource("jboss/datasources/morecatDS")
    );

    return container;
  }

}
