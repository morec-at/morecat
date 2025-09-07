package morecat.api.admin.helper;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import morecat.domain.model.Entry;
import morecat.domain.model.EntryState;
import morecat.util.TimeUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MetaEntry {

  private Long id;
  private String title;
  private String authorName;
  private String createdTime;
  private Set<String> tags;
  private EntryState state;

  public static List<MetaEntry> from(List<Entry> entries) {
    return entries.stream().map(MetaEntry::from).collect(Collectors.toList());
  }

  private static MetaEntry from(Entry entry) {
    return new MetaEntry(
      entry.getId(),
      entry.getTitle(),
      entry.getAuthorName(),
      TimeUtils.composite(entry.getCreatedDate(), entry.getCreatedTime()).toString(),
      entry.getTags(),
      entry.getState()
    );
  }

}
