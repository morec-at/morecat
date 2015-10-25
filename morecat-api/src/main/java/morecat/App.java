package morecat;

import morecat.api.ConfigurationController;
import morecat.api.EntryController;
import morecat.api.MediaController;
import morecat.api.VersionController;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.config.datasources.DataSource;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.datasources.JdbcDriver;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

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
        .jdbcDriver(new JdbcDriver("org.postgresql")
          .driverName("org.postgresql")
          .xaDatasourceClass("org.postgresql.xa.PGXADataSource")
          .driverModuleName("org.postgresql"))
        .dataSource(new DataSource("morecatDS")
          .driverName("org.postgresql")
          .connectionUrl("jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/morecat")
          .userName(DB_USER)
          .password(DB_PASSWORD))
    );

    container.start();

    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);

    deployment.addResource(ConfigurationController.class);
    deployment.addResource(EntryController.class);
    deployment.addResource(MediaController.class);
    deployment.addResource(VersionController.class);

    deployment.addPackages(true, "morecat");

    deployment.addAsWebInfResource(
      new ClassLoaderAsset("META-INF/persistence.xml", App.class.getClassLoader()), "classes/META-INF/persistence.xml");
    deployment.addAsWebInfResource(
      new ClassLoaderAsset("morecat/version.properties", App.class.getClassLoader()), "classes/morecat/version.properties");
    deployment.addAsWebInfResource(
      new ClassLoaderAsset("morecat/git.properties", App.class.getClassLoader()), "classes/morecat/git.properties");
    deployment.addAsWebInfResource(
      new ClassLoaderAsset("db/migration/V1__create_schema.sql", App.class.getClassLoader()), "classes/db/migration/V1__create_schema.sql");
    deployment.addAsWebInfResource(
      new ClassLoaderAsset("db/migration/V1.1__import_initial_data.sql", App.class.getClassLoader()), "classes/db/migration/V1.1__import_initial_data.sql");

    deployment.addAllDependencies();

    container.deploy(deployment);
  }
}
