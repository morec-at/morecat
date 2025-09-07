package morecat.api.admin;

import morecat.MoreCatLogger;
import morecat.domain.model.Configuration;
import morecat.domain.service.ConfigurationService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@ApplicationScoped
@Path("/admin/configurations")
public class ConfigurationController {

  @Inject
  private ConfigurationService configurationService;

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response update(Configuration request) {
    Configuration present = configurationService.find();

    if (present == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }

    present.setBlogName(request.getBlogName());
    present.setBlogDescription(request.getBlogDescription());
    present.setPublicity(request.isPublicity());

    Configuration updated = configurationService.save(present);

    MoreCatLogger.LOGGER.updateConfiguration(updated, "dummy");

    return Response.ok().build();
  }

}
