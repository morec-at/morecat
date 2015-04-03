package morecat.api;

import morecat.MoreCatLogger;
import morecat.api.helper.MediaTypeResolver;
import morecat.api.helper.MetaMedia;
import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.model.Media;
import morecat.domain.service.MediaService;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Optional;

/**
 * @author Yoshimasa Tanabe
 */
@RequestScoped
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

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response upload(@Context UriInfo uriInfo, String request) {

    return Response
        .status(Response.Status.BAD_REQUEST)
        .entity("This API is now not supported. Please use '/upload' instead.").build();

  }

  private String getFileName(MultivaluedMap<String, String> header) {

    String[] contentDisposition = header.getFirst("Content-Disposition").split(";");

    for (String filename : contentDisposition) {
      if ((filename.trim().startsWith("filename"))) {

        String[] name = filename.split("=");

        return name[1].trim().replaceAll("\"", "");
      }
    }

    return "unknown";
  }

  @DELETE
  @Path("/{uuid}/{file-name}")
  public Response delete(@PathParam("uuid") String uuid, @PathParam("file-name") String fileName) {
    Optional<Media> media = mediaService.find(uuid, fileName);

    if (! media.isPresent()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    mediaService.delete(media.get().getId());
    MoreCatLogger.LOGGER.deleteMedia(uuid, fileName, media.get().getAuthorName());

    return Response.noContent().build();
  }

}
