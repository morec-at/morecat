package morecat.domain.service;

import morecat.domain.SiblingEntry;
import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.SinglePage;
import morecat.domain.model.Entry;
import morecat.domain.repository.EntryRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class EntryService {

  @Inject
  private EntryRepository entryRepository;

  public Entry find(long id) {
    return entryRepository.find(id);
  }

  public Page<Entry> findAllByAdmin(Pageable pageable) {
    return entryRepository.findAllByAdmin(pageable);
  }

  public Page<Entry> findAllByAuthor(Pageable pageable, String authorName) {
    return entryRepository.findAllByAuthor(pageable, authorName);
  }

  public Page<Entry> findPublishedEntries(Pageable pageable) {
    return entryRepository.findPublishedEntries(pageable);
  }

  public Page<Entry> findPublishedEntriesByTag(Pageable pageable, String tag) {
    return entryRepository.findPublishedEntriesByTag(pageable, tag);
  }

  public Set<String> findAllPublishedTags() {
    return entryRepository.findAllPublishedTags();
  }

  public Optional<SinglePage<Entry, SiblingEntry>> findPublishedSingleEntry(int year, int month, int day, String permalink) {
    return entryRepository.findPublishedSingleEntry(year, month, day, permalink);
  }

  public Entry save(Entry entry) {
    return entryRepository.save(entry);
  }

  public void delete(Long id) {
    entryRepository.delete(id);
  }

}
