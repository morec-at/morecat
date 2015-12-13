package morecat.api.admin;

import morecat.MoreCatLogger;
import morecat.domain.model.Media;
import morecat.domain.service.MediaService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Optional;

/**
 * @author Yoshimasa Tanabe
 */
@ApplicationScoped
@Path("/admin/media")
public class AdminMediaController {

  @Inject
  private MediaService mediaService;

  /**
   * TODO Now a servlet used for upload a media. please check <a href="https://github.com/emag/morecat/issues/5">this issue</a>.
   *
   * @see morecat.api.MediaUploader#doPost(HttpServletRequest, HttpServletResponse)
   */
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