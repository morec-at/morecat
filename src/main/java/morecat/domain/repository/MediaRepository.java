package morecat.domain.repository;

import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.model.Media;
import morecat.domain.model.Media_;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * @author Yoshimasa Tanabe
 */
@ApplicationScoped
public class MediaRepository extends BaseRepository<Media> {

  @Override
  protected Class<Media> getEntityClass() {
    return Media.class;
  }

  public Page<Media> findAll(Pageable pageable) {
    List<Media> all = getResultList((b, q, media) -> q
        .select(b.construct(Media.class,
          // don't fetch content
          media.get(Media_.uuid), media.get(Media_.name), media.get(Media_.authorName), media.get(Media_.createdTimeStamp)))
        .orderBy(
          b.desc(media.get(Media_.createdTimeStamp)))
        , pageable.getPage() * pageable.getSize(), pageable.getSize());
    return new Page<>(all, count(), pageable);
  }

  /*
   * A large object can be stored in several records, that's why you have to use a transaction. All records are correct or nothing at all.
   *
   * @see <a href="http://stackoverflow.com/a/3164352"></a>
   */
  @Transactional
  public Optional<Media> find(String uuid, String fileName) {
    try {
      return Optional.of(getSingleResult((b, q, media) -> q
        .select(media)
        .where(
          b.equal(media.get(Media_.uuid), uuid),
          b.equal(media.get(Media_.name), fileName)
        )));
    } catch (NoResultException e) {
      return Optional.empty();
    }
  }

}
