package morecat.api.public_.helper;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import morecat.domain.SiblingEntry;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MetaSiblingEntry {

  private final String date;
  private final String permalink;

  public static MetaSiblingEntry from(SiblingEntry siblingEntry) {
    return new MetaSiblingEntry(
      siblingEntry.getDate().toString(),
      siblingEntry.getPermalink());
  }

}
