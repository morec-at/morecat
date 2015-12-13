package morecat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

/**
 * @author Yoshimasa Tanabe
 */
public class MoreCatDeployment {

  public static JAXRSArchive deployment() throws Exception {
    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);

    deployment.addPackages(true, App.class.getPackage());

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

    return deployment;
  }

}
