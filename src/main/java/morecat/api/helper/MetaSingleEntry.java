package morecat.api.helper;

import lombok.AllArgsConstructor;
import lombok.Data;
import morecat.domain.model.Entry;
import morecat.util.TimeUtils;

import java.util.Set;

@Data
@AllArgsConstructor
public class MetaSingleEntry {

  private String title;
  private String content;
  private String permalink;
  private String authorName;
  private String createdTime;
  private Set<String> tags;

  public static MetaSingleEntry from(Entry entry) {
    return new MetaSingleEntry(
      entry.getTitle(),
      entry.getContent(),
      entry.getPermalink(),
      entry.getAuthorName(),
      TimeUtils.composite(entry.getCreatedDate(), entry.getCreatedTime()).toString(),
      entry.getTags());
  }

}
