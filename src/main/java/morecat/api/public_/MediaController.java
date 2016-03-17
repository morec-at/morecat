package morecat.api.public_;

import morecat.api.helper.MediaTypeResolver;
import morecat.api.helper.MetaMedia;
import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.model.Media;
import morecat.domain.service.MediaService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

@ApplicationScoped
@Path("/media")
public class MediaController {

  @Inject
  private MediaService mediaService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Page<MetaMedia> get(@QueryParam("size") @DefaultValue("5") int size,
                             @QueryParam("page") @DefaultValue("0") int page) {

    Page<Media> mediums = mediaService.findAll(new Pageable(size, page));
    return mediums.convert(MetaMedia.from(mediums.getElements()));
  }

  @GET
  @Path("/{uuid}/{file-name}")
  public Response get(@PathParam("uuid") String uuid, @PathParam("file-name") String fileName) {
    Optional<Media> media = mediaService.find(uuid, fileName);

    if (! media.isPresent()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    return Response
      .ok(media.get().getContent(), MediaTypeResolver.resolve(fileName))
      .build();
  }

}
