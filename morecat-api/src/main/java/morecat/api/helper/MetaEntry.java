package morecat.api.helper;

import lombok.AllArgsConstructor;
import lombok.Data;
import morecat.domain.model.Entry;
import morecat.util.TimeUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Yoshimasa Tanabe
 */
@Data
@AllArgsConstructor
public class MetaEntry {

  private String title;
  private String permalink;
  private String authorName;
  private LocalDateTime createdTime;
  private Set<String> tags;

  public static List<MetaEntry> from(List<Entry> entries) {
    return entries.stream().map(MetaEntry::from).collect(Collectors.toList());
  }

  private static MetaEntry from(Entry entry) {
    return new MetaEntry(
      entry.getTitle(), entry.getPermalink(), entry.getAuthorName(), TimeUtils.composite(entry.getCreatedDate(), entry.getCreatedTime()), entry.getTags());
  }

}
