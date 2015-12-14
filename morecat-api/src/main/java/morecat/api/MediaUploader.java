package morecat.api;

import morecat.MoreCatLogger;
import morecat.domain.model.Media;
import morecat.domain.service.MediaService;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Yoshimasa Tanabe
 */
@WebServlet("/upload")
@MultipartConfig
public class MediaUploader extends HttpServlet {

  @Inject
  private MediaService mediaService;

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Part file = request.getPart("file");

    if (file == null) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      response.getWriter().println("'file' parameter is required.");
      return;
    }

    Media media = new Media();
    media.setAuthorName("dummy"); // FIXME

    media.setName(file.getSubmittedFileName());

    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         InputStream is = file.getInputStream()) {
      int length = 0;
      final byte[] buffer = new byte[1024];
      while ((length = is.read(buffer)) != -1) {
        bos.write(buffer, 0, length);
      }
      media.setContent(bos.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Media uploaded = mediaService.save(media);
    MoreCatLogger.LOGGER.uploadMedia(uploaded.getUuid(), uploaded.getName(), uploaded.getAuthorName());

    response.setStatus(HttpServletResponse.SC_CREATED);
    String mediaApiBasePath = request.getRequestURL().toString().replaceFirst("upload", "media");
    response.setHeader(
      "Location",
      String.format("%s/%s/%s", mediaApiBasePath, uploaded.getUuid(), uploaded.getName()));
  }

}
