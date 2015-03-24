package morecat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.gradle.archive.importer.embedded.EmbeddedGradleImporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * @author Yoshimasa Tanabe
 */
public class MoreCatDeployment {

  public static WebArchive deployment() {
    return ShrinkWrap
      .create(EmbeddedGradleImporter.class, "test.war")
      .forThisProjectDirectory()
      .importBuildOutput().as(WebArchive.class);
//      .addAsResource("META-INF/test-persistence.xml", "META-INF/persistence.xml")
//      .addAsWebInfResource("test-ds.xml", "morecat-ds.xml");
  }

}
