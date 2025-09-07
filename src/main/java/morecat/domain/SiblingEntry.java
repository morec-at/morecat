package morecat.domain;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SiblingEntry {

  private final LocalDate date;
  private final String permalink;

}
