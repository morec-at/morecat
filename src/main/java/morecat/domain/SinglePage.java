package morecat.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class SinglePage<T, R> {

  private T element;
  private R next;
  private R previous;

  public <E, S> SinglePage<E, S> convert(E element, S next, S previous) {
    return new SinglePage<E, S>(element, next, previous);
  }

}
