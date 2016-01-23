package morecat.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Yoshimasa Tanabe
 */
@Data
@AllArgsConstructor
public class Pageable {

  private final int size;
  private final int page;

}
