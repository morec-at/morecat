package morecat;

import morecat.api.ConfigurationController;
import morecat.api.EntryController;
import morecat.api.MediaController;
import morecat.api.VersionController;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jaxrs.JAXRSArchive;
import org.wildfly.swarm.jpa.JPAFraction;

/**
 * @author Yoshimasa Tanabe
 */
public class App {

  private static final String DB_HOST = MoreCatConfiguration.getDBHost();
  private static final String DB_PORT = MoreCatConfiguration.getDBPort();
  private static final String DB_USER = MoreCatConfiguration.getDBUser();
  private static final String DB_PASSWORD = MoreCatConfiguration.getDBPassword();

  public static void main(String[] args) throws Exception {
    Container container = new Container();

    container.fraction(new DatasourcesFraction()
      .jdbcDriver("org.postgresql", (d) -> {
        d.driverDatasourceClassName("org.postgresql.Driver");
        d.xaDatasourceClass("org.postgresql.xa.PGXADataSource");
        d.driverModuleName("org.postgresql");
      })
      .dataSource("morecatDS", (ds) -> {
        ds.driverName("org.postgresql");
        ds.connectionUrl("jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/morecat");
        ds.userName(DB_USER);
        ds.password(DB_PASSWORD);
      })
    );

    container.fraction(new JPAFraction()
      .inhibitDefaultDatasource()
      .defaultDatasource("jboss/datasources/morecatDS")
    );

    container
      .start()
      .deploy(MoreCatDeployment.deployment());

  }
}
