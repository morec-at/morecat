package morecat.domain.service;

import morecat.MoreCatDeployment;
import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.SiblingEntry;
import morecat.domain.SinglePage;
import morecat.domain.model.Entry;
import morecat.domain.model.EntryFormat;
import morecat.domain.model.EntryState;
import morecat.domain.repository.EntryRepository;
import morecat.util.StringUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.*;

/**
 * @author Yoshimasa Tanabe
 */
public class EntryServiceIT {

  private static JavaArchive deploymentEntryServiceIT() {
    return MoreCatDeployment.deployment().addClass(EntryServiceIT.class);
  }


  @RunWith(Arquillian.class)
  public static class should_set_random_permalink {
    @Deployment
    public static JavaArchive deployment() {
      return deploymentEntryServiceIT();
    }

    @Inject
    private EntryService sut;

    @Test
    public void blank_permalink() throws Exception {
      Entry created = sut.save(new Entry("title", "content", /* blank permalink */ "", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN));
      Assert.assertThat(StringUtils.isBlank(created.getPermalink()), is(false));
    }

    @Test
    public void null_permalink() throws Exception {
      Entry created = sut.save(new Entry("title", "content", /* null permalink */ null, "author", EntryState.PUBLIC, EntryFormat.MARKDOWN));
      Assert.assertThat(StringUtils.isBlank(created.getPermalink()), is(false));
    }
  }

  @RunWith(Arquillian.class)
  public static class populate_entries_draft_1_public_1 {

    @Deployment
    public static JavaArchive deployment() {
      return deploymentEntryServiceIT();
    }

    @Inject
    private EntryService sut;

    @Inject
    private EntryRepository entryRepository;

    @Before
    public void setUp() throws Exception {
      entryRepository.deleteAll();

      Entry entry1 = new Entry("title1", "content1", "permalink1", "author1", EntryState.DRAFT, EntryFormat.MARKDOWN);
      Entry entry2 = new Entry("title2", "content2", "permalink2", "author2", EntryState.PUBLIC, EntryFormat.MARKDOWN);

      List<Entry> entries = new ArrayList<>();
      entries.add(entry1);
      entries.add(entry2);

      entries.forEach(entryRepository::save);
    }

    @Test
    public void should_collect_all_entries() throws Exception {
      Page<Entry> allEntries = sut.findAllByAdmin(new Pageable(2, 0));
      Assert.assertThat(allEntries.getElements().size(), is(2));
    }

    @Test
    public void should_collect_all_entries_by_a_author() throws Exception {
      Page<Entry> allEntries = sut.findAllByAuthor(new Pageable(2, 0), "author1");
      Assert.assertThat(allEntries.getElements().size(), is(1));
    }

    @Test
    public void should_collect_published_entries() throws Exception {
      Page<Entry> publishedEntries = sut.findPublishedEntries(new Pageable(2, 0));
      Assert.assertThat(publishedEntries.getElements().size(), is(1));
    }
  }

  @RunWith(Arquillian.class)
  public static class pagination_entries_draft_1_public_5 {

    @Deployment
    public static JavaArchive deployment() {
      return deploymentEntryServiceIT();
    }

    @Inject
    private EntryService sut;

    @Inject
    private EntryRepository entryRepository;

    @Before
    public void setUp() throws Exception {
      entryRepository.deleteAll();

      Entry entry1 = new Entry("title1", "content1", "permalink1", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      Entry entry2 = new Entry("title2", "content2", "permalink2", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      Entry entry3 = new Entry("title3", "content3", "permalink3", "author", EntryState.DRAFT, EntryFormat.MARKDOWN);
      Entry entry4 = new Entry("title4", "content4", "permalink4", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      Entry entry5 = new Entry("title5", "content5", "permalink5", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      Entry entry6 = new Entry("title6", "content6", "permalink6", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);

      List<Entry> entries = new ArrayList<>();
      entries.add(entry1);
      entries.add(entry2);
      entries.add(entry3);
      entries.add(entry4);
      entries.add(entry5);
      entries.add(entry6);

      entries.forEach(entryRepository::save);
    }

    @Test
    public void pagination_published_size_3_page_0_total_6() throws Exception {
      Page<Entry> publishedEntries = sut.findPublishedEntries(new Pageable(3, 0));

      Assert.assertThat(publishedEntries.isFirstPage(), is(true));
      Assert.assertThat(publishedEntries.isLastPage(), is(false));
      Assert.assertThat(publishedEntries.getTotalNumberOfPages(), is(2L));
      Assert.assertThat(publishedEntries.getCurrentPageSize(), is(3L));
    }

    @Test
    public void pagination_published_size_3_page_1_total_6() throws Exception {
      Page<Entry> publishedEntries = sut.findPublishedEntries(new Pageable(3, 1));

      Assert.assertThat(publishedEntries.isFirstPage(), is(false));
      Assert.assertThat(publishedEntries.isLastPage(), is(true));
      Assert.assertThat(publishedEntries.getTotalNumberOfPages(), is(2L));
      Assert.assertThat(publishedEntries.getCurrentPageSize(), is(2L)); // 1 draft
    }

  }

  @RunWith(Arquillian.class)
  public static class populate_tags_draft_1_public_2 {

    @Deployment
    public static JavaArchive deployment() {
      return deploymentEntryServiceIT();
    }

    @Inject
    private EntryService sut;

    @Inject
    private EntryRepository entryRepository;

    @Before
    public void setUp() throws Exception {
      entryRepository.deleteAll();

      Entry entry1 = new Entry("title1", "content1", "permalink1", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      entry1.setTags(new HashSet<String>() {{
        add("tag1");
        add("tag2");
      }});

      Entry entry2 = new Entry("title2", "content2", "permalink2", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      entry2.setTags(new HashSet<String>() {{
        add("tag2");
        add("tag3");
      }});

      Entry entry3 = new Entry("title3", "content3", "permalink3", "author", EntryState.DRAFT, EntryFormat.MARKDOWN);
      entry3.setTags(new HashSet<String>() {{
        add("tag3");
        add("tag4");
      }});

      List<Entry> entries = new ArrayList<>();
      entries.add(entry1);
      entries.add(entry2);
      entries.add(entry3);

      entries.forEach(entryRepository::save);
    }

    @Test
    public void should_populate_published_tags() throws Exception {
      Set<String> allPublishedTags = sut.findAllPublishedTags();

      Assert.assertThat(allPublishedTags.size(), is(3));
      Assert.assertThat(allPublishedTags.contains("tag4"), is(false));
    }
  }

  @RunWith(Arquillian.class)
  public static class pagination_tags_draft_1_public_2 {
    @Deployment
    public static JavaArchive deployment() {
      return deploymentEntryServiceIT();
    }

    @Inject
    private EntryService sut;

    @Inject
    private EntryRepository entryRepository;

    @Before
    public void setUp() throws Exception {
      entryRepository.deleteAll();

      Entry entry1 = new Entry("title1", "content1", "permalink1", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      entry1.setTags(new HashSet<String>() {{
        add("tag1");
      }});

      Entry entry2 = new Entry("title2", "content2", "permalink2", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      entry2.setTags(new HashSet<String>() {{
        add("tag1");
      }});

      Entry entry3 = new Entry("title3", "content3", "permalink3", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      entry3.setTags(new HashSet<String>() {{
        add("tag1");
      }});

      Entry entry4 = new Entry("title4", "content4", "permalink4", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      entry4.setTags(new HashSet<String>() {{
        add("tag1");
      }});

      Entry entry5 = new Entry("title5", "content5", "permalink5", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      entry5.setTags(new HashSet<String>() {{
        add("tag1");
      }});

      Entry entry6 = new Entry("title6", "content6", "permalink6", "author", EntryState.DRAFT, EntryFormat.MARKDOWN);
      entry6.setTags(new HashSet<String>() {{
        add("tag1");
      }});

      List<Entry> entries = new ArrayList<>();
      entries.add(entry1);
      entries.add(entry2);
      entries.add(entry3);
      entries.add(entry4);
      entries.add(entry5);
      entries.add(entry6);

      entries.forEach(entryRepository::save);

    }

    @Test
    public void pagination_published_size_3_page_0_total_6() throws Exception {
      Page<Entry> publishedEntries = sut.findPublishedEntriesByTag(new Pageable(3, 0), "tag1");

      Assert.assertThat(publishedEntries.isFirstPage(), is(true));
      Assert.assertThat(publishedEntries.isLastPage(), is(false));
      Assert.assertThat(publishedEntries.getTotalNumberOfPages(), is(2L));
      Assert.assertThat(publishedEntries.getCurrentPageSize(), is(3L));
    }

    @Test
    public void pagination_published_size_3_page_1_total_6() throws Exception {
      Page<Entry> publishedEntries = sut.findPublishedEntriesByTag(new Pageable(3, 1), "tag1");

      Assert.assertThat(publishedEntries.isFirstPage(), is(false));
      Assert.assertThat(publishedEntries.isLastPage(), is(true));
      Assert.assertThat(publishedEntries.getTotalNumberOfPages(), is(2L));
      Assert.assertThat(publishedEntries.getCurrentPageSize(), is(2L)); // 1 draft
    }
  }

  @RunWith(Arquillian.class)
  public static class check_single_entry_draft_1_public_3 {

    @Deployment
    public static JavaArchive deployment() {
      return deploymentEntryServiceIT();
    }

    @Inject
    private EntryService sut;

    @Inject
    private EntryRepository entryRepository;

    @Before
    public void setUp() throws Exception {
      entryRepository.deleteAll();

      List<Entry> entries = new ArrayList<>();

      Entry entry1 = new Entry("title1", "content1", "permalink1", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      Entry entry2 = new Entry("title2", "content2", "permalink2", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);
      Entry entry3 = new Entry("title3", "content3", "permalink3", "author", EntryState.DRAFT, EntryFormat.MARKDOWN);
      Entry entry4 = new Entry("title4", "content4", "permalink4", "author", EntryState.PUBLIC, EntryFormat.MARKDOWN);

      entries.add(entry1);
      entries.add(entry2);
      entries.add(entry3);
      entries.add(entry4);

      entries.forEach(entryRepository::save);

      entry1.setCreatedDate(LocalDate.of(2015, Month.APRIL, 1));
      entry2.setCreatedDate(LocalDate.of(2015, Month.APRIL, 2));
      entry3.setCreatedDate(LocalDate.of(2015, Month.APRIL, 3));
      entry4.setCreatedDate(LocalDate.of(2015, Month.APRIL, 4));

      entries.forEach(entryRepository::save);
    }

    @Test
    public void should_have_only_next() throws Exception {
      SinglePage<Entry, SiblingEntry> permalink6 = sut.findPublishedSingleEntry(2015, 4, 1, "permalink1").get();

      Assert.assertThat(permalink6.getNext(), is(notNullValue()));
      Assert.assertThat(permalink6.getNext().getPermalink(), is("permalink2"));
      Assert.assertThat(permalink6.getPrevious(), is(nullValue()));
    }

    @Test
    public void should_have_only_previous() throws Exception {
      SinglePage<Entry, SiblingEntry> permalink4 = sut.findPublishedSingleEntry(2015, 4, 4, "permalink4").get();

      Assert.assertThat(permalink4.getNext(), is(nullValue()));
      Assert.assertThat(permalink4.getPrevious(), is(notNullValue()));
      Assert.assertThat(permalink4.getPrevious().getPermalink(), is("permalink2")); // permalink3 is draft
    }

    @Test
    public void should_have_previous_and_next() throws Exception {
      SinglePage<Entry, SiblingEntry> permalink2 = sut.findPublishedSingleEntry(2015, 4, 2, "permalink2").get();

      Assert.assertThat(permalink2.getNext(), is(notNullValue()));
      Assert.assertThat(permalink2.getNext().getPermalink(), is("permalink4")); // permalink3 is draft
      Assert.assertThat(permalink2.getPrevious(), is(notNullValue()));
      Assert.assertThat(permalink2.getPrevious().getPermalink(), is("permalink1"));
    }
  }

}