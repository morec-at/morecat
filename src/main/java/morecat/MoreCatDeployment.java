package morecat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

import java.util.Arrays;

public class MoreCatDeployment {

  private MoreCatDeployment() {}

  public static JAXRSArchive deployment() throws Exception {
    JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);

    deployment.addPackages(true, App.class.getPackage());

    toMetaInf(deployment, "persistence.xml");
    toClasses(deployment,
        "morecat/version.properties",
        "morecat/git.properties",
        "db/migration/V1__create_schema.sql",
        "db/migration/V1.1__import_initial_data.sql");

    deployment.addAllDependencies();

    return deployment;
  }

  private static void toMetaInf(JAXRSArchive deployment, String... paths) {
    Arrays.stream(paths).forEach(path ->
        add(deployment, "META-INF/" + path, "classes/META-INF/" + path)
    );
  }

  private static void toClasses(JAXRSArchive deployment, String... paths) {
    Arrays.stream(paths).forEach(path ->
        add(deployment, path, "classes/" + path)
    );
  }

  private static void add(JAXRSArchive deployment, String from, String to) {
    deployment.addAsWebInfResource(new ClassLoaderAsset(from, App.class.getClassLoader()), to);
  }

}
