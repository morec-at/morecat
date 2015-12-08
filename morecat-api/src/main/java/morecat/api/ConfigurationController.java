package morecat.api;

import morecat.domain.model.Configuration;
import morecat.domain.service.ConfigurationService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author Yoshimasa Tanabe
 */
@ApplicationScoped
@Path("/configurations")
public class ConfigurationController {

  @Inject
  private ConfigurationService configurationService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Configuration getBlogName() {
    return configurationService.find();
  }

}
