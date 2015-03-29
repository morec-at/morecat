package morecat.domain.repository;

import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.model.Media;
import morecat.domain.model.Media_;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.NoResultException;
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
        .select(media)
        .orderBy(
          b.desc(media.get(Media_.createdTime))
        )
    );
    return new Page<>(all, count(), pageable);
  }

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
