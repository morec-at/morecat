package morecat.admin.web;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.container.Container;
import org.wildfly.swarm.undertow.WARArchive;

/**
 * @author Yoshimasa Tanabe
 */
public class App {

  public static void main(String[] args) throws Exception {

    Container container = new Container();

    WARArchive deployment = ShrinkWrap.create(WARArchive.class);

    deployment.addPackages(true, App.class.getPackage());

    deployment.addAsWebResource(
      new ClassLoaderAsset("index.html", App.class.getClassLoader()), "index.html");
    deployment.addAsWebResource(
      new ClassLoaderAsset("index.xhtml", App.class.getClassLoader()), "index.xhtml");
    deployment.addAsWebResource(
      new ClassLoaderAsset("entries/list.xhtml", App.class.getClassLoader()), "entries/list.xhtml");

    deployment.addAsWebInfResource(
      new ClassLoaderAsset("WEB-INF/web.xml", App.class.getClassLoader()), "web.xml");
    deployment.addAsWebInfResource(
      new ClassLoaderAsset("WEB-INF/template.xhtml", App.class.getClassLoader()), "template.xhtml");

    deployment.addAllDependencies();

    container.start().deploy(deployment);
  }

}
