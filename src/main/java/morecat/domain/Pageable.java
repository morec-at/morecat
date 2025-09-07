package morecat.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Pageable {

  private final int size;
  private final int page;

}
