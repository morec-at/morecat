package morecat.api.admin.helper;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import morecat.domain.model.Entry;
import morecat.domain.model.EntryFormat;
import morecat.domain.model.EntryState;
import morecat.util.TimeUtils;

import java.util.Set;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MetaSingleEntry {

  private Long id;
  private String title;
  private String content;
  private String permalink;
  private String authorName;
  private String createdTime;
  private Set<String> tags;
  private EntryState state;
  private EntryFormat format;

  public static MetaSingleEntry from(Entry entry) {
    return new MetaSingleEntry(
      entry.getId(),
      entry.getTitle(),
      entry.getContent(),
      entry.getPermalink(),
      entry.getAuthorName(),
      TimeUtils.composite(entry.getCreatedDate(), entry.getCreatedTime()).toString(),
      entry.getTags(),
      entry.getState(),
      entry.getFormat()
    );
  }

}
