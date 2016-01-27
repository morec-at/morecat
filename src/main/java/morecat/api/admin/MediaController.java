package morecat.api.admin;

import morecat.MoreCatLogger;
import morecat.domain.model.Media;
import morecat.domain.service.MediaService;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Yoshimasa Tanabe
 */
@ApplicationScoped
@Path("/admin/media")
public class MediaController {

  @Inject
  private MediaService mediaService;

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response upload(@Context UriInfo uriInfo, MultipartFormDataInput input) {

    Map<String, List<InputPart>> uploadForm = input.getFormDataMap();

    if (uploadForm.get("file") == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("'file' parameter is required.").build();
    }

    InputPart file = uploadForm.get("file").get(0);

    Media media = new Media();
    media.setAuthorName("dummy");

    MultivaluedMap<String, String> headers = file.getHeaders();
    String fileName = getFileName(headers);
    media.setName(fileName);

    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()){
      InputStream is = file.getBody(InputStream.class, null);
      int length;
      byte[] buffer = new byte[1024];
      while ((length = is.read(buffer)) != -1) {
        bos.write(buffer, 0, length);
      }
      media.setContent(bos.toByteArray());
    } catch (IOException e) {
      e.printStackTrace();
      return Response.serverError().entity("upload failed").build();
    }

    Media uploaded = mediaService.save(media);
    MoreCatLogger.LOGGER.uploadMedia(uploaded.getUuid(), uploaded.getName(), uploaded.getAuthorName());

    return Response
      .created(uriInfo.getBaseUriBuilder()
        .path("media")
        .path(uploaded.getUuid())
        .path(uploaded.getName())
        .build())
      .build();
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
