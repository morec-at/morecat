package morecat.domain.repository;

import morecat.domain.SiblingEntry;
import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.SinglePage;
import morecat.domain.model.Entry;
import morecat.domain.model.EntryState;
import morecat.domain.model.Entry_;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.NoResultException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class EntryRepository extends BaseRepository<Entry> {

  @Override
  protected Class<Entry> getEntityClass() {
    return Entry.class;
  }

  public Page<Entry> findAllByAdmin(Pageable pageable) {
    List<Entry> all = getResultList((b, q, entry) -> q
      .select(entry)
      .orderBy(
        b.desc(entry.get(Entry_.createdDate)),
        b.desc(entry.get(Entry_.createdTime)))
      , pageable.getPage() * pageable.getSize(), pageable.getSize());

    return new Page<>(all, count(), pageable);
  }

  public Page<Entry> findAllByAuthor(Pageable pageable, String authorName) {
    List<Entry> authors = getResultList((b, q, entry) -> q
      .select(entry)
      .where(
        b.equal(entry.get(Entry_.authorName), authorName))
      .orderBy(
        b.desc(entry.get(Entry_.createdDate)),
        b.desc(entry.get(Entry_.createdTime)))
      , pageable.getPage() * pageable.getSize(), pageable.getSize());

    Long totalNumberOfAuthors = getSingleResult((b, q, entry) -> q
      .select(b.count(entry))
      .where(
        b.equal(entry.get(Entry_.authorName), authorName))
      , Long.class);

    return new Page<>(authors, totalNumberOfAuthors, pageable);
  }

  public Page<Entry> findPublishedEntries(Pageable pageable) {
    List<Entry> published = getResultList((b, q, entry) -> q
      .select(entry)
      .where(
        b.equal(entry.get(Entry_.state), EntryState.PUBLIC))
      .orderBy(
        b.desc(entry.get(Entry_.createdDate)),
        b.desc(entry.get(Entry_.createdTime)))
      , pageable.getPage() * pageable.getSize(), pageable.getSize());

    Long totalNumberOfPublished = getSingleResult((b, q, entry) -> q
      .select(b.count(entry))
      .where(
        b.equal(entry.get(Entry_.state), EntryState.PUBLIC))
      , Long.class);

    return new Page<>(published, totalNumberOfPublished, pageable);
  }

  public Page<Entry> findPublishedEntriesByTag(Pageable pageable, String tag) {
    List<Entry> publishedByTag = getResultList((b, q, entry) -> q
      .select(entry)
      .where(
        b.equal(entry.get(Entry_.state), EntryState.PUBLIC),
        b.isMember(tag, entry.get(Entry_.tags)))
      .orderBy(
        b.desc(entry.get(Entry_.createdDate)),
        b.desc(entry.get(Entry_.createdTime)))
      , pageable.getPage() * pageable.getSize(), pageable.getSize());

    Long totalNumberOfPublishedBYTag = getSingleResult((b, q, entry) -> q
      .select(b.count(entry))
      .where(
        b.equal(entry.get(Entry_.state), EntryState.PUBLIC),
        b.isMember(tag, entry.get(Entry_.tags)))
      , Long.class);

    return new Page<>(publishedByTag, totalNumberOfPublishedBYTag, pageable);
  }

  public Set<String> findAllPublishedTags() {
    return findAllPublished().stream()
      .flatMap(entry -> entry.getTags().stream())
      .distinct()
      .collect(Collectors.toSet());
  }

  public Optional<SinglePage<Entry, SiblingEntry>> findPublishedSingleEntry(int year, int month, int day, String permalink) {
    SinglePage<Entry, SiblingEntry> single = new SinglePage<>();

    Entry anEntry = null;
    try {
      anEntry = getSingleResult((b, q, entry) -> q
        .select(entry)
        .where(
          b.equal(entry.get(Entry_.state), EntryState.PUBLIC),
          b.equal(b.function("year", Integer.class, entry.get(Entry_.createdDate)), year),
          b.equal(b.function("month", Integer.class, entry.get(Entry_.createdDate)), month),
          b.equal(b.function("day", Integer.class, entry.get(Entry_.createdDate)), day),
          b.equal(entry.get(Entry_.permalink), permalink)));

      single.setElement(anEntry);
    } catch (NoResultException e) {
      return Optional.empty();
    }

    List<Entry> allPublished = findAllPublished();

    for (int i = 0; i < allPublished.size(); i++) {
      Entry target = allPublished.get(i);
      if (target.equals(anEntry)) {
        setNext(single, allPublished, i);
        setPrevious(single, allPublished, i);
      }
      if (single.getNext() != null && single.getPrevious() != null) {
        break;
      }
    }

    return Optional.of(single);
  }

  private List<Entry> findAllPublished() {
    return getResultList((b, q, entry) -> q
      .select(entry)
      .where(
        b.equal(entry.get(Entry_.state), EntryState.PUBLIC))
      .orderBy(
        b.desc(entry.get(Entry_.createdDate)),
        b.desc(entry.get(Entry_.createdTime))));
  }

  private void setNext(SinglePage<Entry, SiblingEntry> single, List<Entry> allPublished, int i) {
    if (i  > 0) {
      Entry next = allPublished.get(i - 1);
      single.setNext(new SiblingEntry(next.getCreatedDate(), next.getPermalink()));
    } else {
      single.setNext(null); // most newest entry
    }
  }

  private void setPrevious(SinglePage<Entry, SiblingEntry> single, List<Entry> allPublished, int i) {
    if (i + 1 < allPublished.size()) {
      Entry previous = allPublished.get(i + 1);
      single.setPrevious(new SiblingEntry(previous.getCreatedDate(), previous.getPermalink()));
    } else {
      single.setPrevious(null); // most oldest entry
    }
  }

}
