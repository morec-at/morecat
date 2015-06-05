package morecat;

import morecat.api.ConfigurationController;
import morecat.api.EntryController;
import morecat.api.MediaController;
import morecat.api.VersionController;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.datasources.Datasource;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.datasources.Driver;
import org.wildfly.swarm.jaxrs.JAXRSDeployment;

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

    container.subsystem(new DatasourcesFraction()
        .driver(
          new Driver("org.postgresql")
            .xaDatasourceClassName("org.postgresql.xa.PGXADataSource")
            .module("org.postgresql"))
        .datasource(new Datasource("morecatDS")
          .driver("org.postgresql")
          .connectionURL("jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/morecat")
          .authentication(DB_USER, DB_PASSWORD))
    );

    container.start();

    JAXRSDeployment jaxRsDeployment = new JAXRSDeployment(container);

    jaxRsDeployment.addResource(ConfigurationController.class);
    jaxRsDeployment.addResource(EntryController.class);
    jaxRsDeployment.addResource(MediaController.class);
    jaxRsDeployment.addResource(VersionController.class);

    jaxRsDeployment.getArchive().addPackages(true, "morecat");

    jaxRsDeployment.getArchive().addAsWebInfResource(
      new ClassLoaderAsset("META-INF/persistence.xml", App.class.getClassLoader()), "classes/META-INF/persistence.xml");
    jaxRsDeployment.getArchive().addAsWebInfResource(
      new ClassLoaderAsset("morecat/version.properties", App.class.getClassLoader()), "classes/morecat/version.properties");
    jaxRsDeployment.getArchive().addAsWebInfResource(
      new ClassLoaderAsset("morecat/git.properties", App.class.getClassLoader()), "classes/morecat/git.properties");
    jaxRsDeployment.getArchive().addAsWebInfResource(
      new ClassLoaderAsset("db/migration/V1__create_schema.sql", App.class.getClassLoader()), "classes/db/migration/V1__create_schema.sql");
    jaxRsDeployment.getArchive().addAsWebInfResource(
      new ClassLoaderAsset("db/migration/V1.1__import_initial_data.sql", App.class.getClassLoader()), "classes/db/migration/V1.1__import_initial_data.sql");

    container.deploy(jaxRsDeployment);
  }
}
