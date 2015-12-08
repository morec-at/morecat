package morecat.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author Yoshimasa Tanabe
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Page<T> {

  private List<T> elements;
  private long totalNumberOfElements;
  private long totalNumberOfPages;
  private int size;
  private int page;
  private long currentPageSize;
  private boolean firstPage;
  private boolean lastPage;

  public Page(List<T> elements, long totalNumberOfElements, Pageable pageable) {
    this.size = pageable.getSize();
    this.page = pageable.getPage();

    this.elements = elements;
    this.totalNumberOfElements = totalNumberOfElements;
    this.totalNumberOfPages = (totalNumberOfElements / size) + ( (totalNumberOfElements % size) == 0 ? 0 : 1 );
    this.currentPageSize = elements.size();
    this.firstPage = (page == 0);
    this.lastPage = ( (page + 1) == totalNumberOfPages );
  }

  public <R> Page<R> convert(List<R> elements) {
    return new Page<R>(
      elements, totalNumberOfElements, totalNumberOfPages, size, page, currentPageSize, firstPage, lastPage);
  }

}
