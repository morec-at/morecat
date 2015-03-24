package morecat.api;

import morecat.Version;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

/**
 * @author Yoshimasa Tanabe
 */
@ApplicationScoped
@Path("/version")
public class VersionController {

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String getVersion() {
    return Version.getVersion();
  }

  @GET
  @Path("/git-commit-id")
  @Produces(MediaType.TEXT_PLAIN)
  public String getGitCommitId(@QueryParam("short") boolean isShort) {
    if (isShort) {
      return Version.getGitCommitIdShort();
    } else {
      return Version.getGitCommitId();
    }
  }

}
