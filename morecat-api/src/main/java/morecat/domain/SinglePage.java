package morecat.domain;

import lombok.Data;

import java.util.Optional;

/**
 * @author Yoshimasa Tanabe
 */
@Data
public class SinglePage<T> {

  private T element;
  private Optional<T> next;
  private Optional<T> previous;

}
