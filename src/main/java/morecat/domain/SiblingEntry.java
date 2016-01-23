package morecat.domain;

import lombok.Data;

import java.time.LocalDate;

/**
 * @author Yoshimasa Tanabe
 */
@Data
public class SiblingEntry {

  private final LocalDate date;
  private final String permalink;

}
