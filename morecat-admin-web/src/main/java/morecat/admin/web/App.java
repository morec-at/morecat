package morecat.admin.web;

import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.undertow.DefaultWarDeployment;
import org.wildfly.swarm.undertow.WarDeployment;

/**
 * @author Yoshimasa Tanabe
 */
public class App {

  public static void main(String[] args) throws Exception {

    Container container = new Container();

    WarDeployment deployment = new DefaultWarDeployment(container);

    deployment.getArchive().addPackages(true, App.class.getPackage());

    deployment.getArchive().addAsWebResource(
      new ClassLoaderAsset("index.html", App.class.getClassLoader()), "index.html");
    deployment.getArchive().addAsWebResource(
      new ClassLoaderAsset("index.xhtml", App.class.getClassLoader()), "index.xhtml");

    deployment.getArchive().addAsWebInfResource(
      new ClassLoaderAsset("WEB-INF/web.xml", App.class.getClassLoader()), "web.xml");
    deployment.getArchive().addAsWebInfResource(
      new ClassLoaderAsset("WEB-INF/template.xhtml", App.class.getClassLoader()), "template.xhtml");

    container.start().deploy(deployment);
  }

}
