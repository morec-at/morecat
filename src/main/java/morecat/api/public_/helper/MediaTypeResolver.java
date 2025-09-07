package morecat.api.public_.helper;

import java.util.HashMap;
import java.util.Map;

public class MediaTypeResolver {

  private static final Map<String, String> mediaTypes;

  static {
    mediaTypes = new HashMap<String, String>() {{
      put("json", "application/json");
      put("xml", "application/xml");
      put("zip", "application/zip");
      put("pdf", "application/pdf");
      put("gif", "image/gif");
      put("jpeg", "image/jpeg");
      put("jpg", "image/jpeg");
      put("png", "image/png");
      put("html", "text/html");
      put("text", "text/plain");
      put("txt", "text/plain");
      put("xhtml", "text/xml");
    }};
  }

  public static String resolve(String fileName) {
    return mediaTypes.getOrDefault(getFileExtension(fileName), "application/octet-stream");
  }

  private static String getFileExtension(String fileName) {
    String[] dotSplit = fileName.split("\\.");
    return dotSplit[dotSplit.length - 1].toLowerCase();
  }

}
