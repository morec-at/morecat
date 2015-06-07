package morecat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import java.io.File;

/**
 * @author Yoshimasa Tanabe
 */
public class MoreCatDeployment {

  public static JavaArchive deployment() {
    return ShrinkWrap.createFromZipFile(
      JavaArchive.class,
      new File("target/morecat-api-swarm.jar")
    );
  }

}
