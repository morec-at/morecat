package morecat.api.helper;

import lombok.Data;
import morecat.domain.model.Media;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class MetaMedia {

  private final String uuid;
  private final String name;
  private final String authorName;
  private final LocalDateTime createdTimeStamp;

  public static List<MetaMedia> from(List<Media> mediums) {
    return mediums.stream().map(MetaMedia::from).collect(Collectors.toList());
  }

  private static MetaMedia from(Media media) {
    return new MetaMedia(
      media.getUuid(), media.getName(), media.getAuthorName(), media.getCreatedTimeStamp());
  }

}
