package morecat.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Yoshimasa Tanabe
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class SinglePage<T, R> {

  private T element;
  private R next;
  private R previous;

  public <S> SinglePage<S, R> convert(S element) {
    return new SinglePage<S, R>(element, next, previous);
  }
}
