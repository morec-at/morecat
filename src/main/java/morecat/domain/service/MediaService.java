package morecat.domain.service;

import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.model.Media;
import morecat.domain.repository.MediaRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class MediaService {

  @Inject
  private MediaRepository mediaRepository;

  public Page<Media> findAll(Pageable pageable) {
    return mediaRepository.findAll(pageable);
  }

  public Optional<Media> find(String uuid, String fileName) {
    return mediaRepository.find(uuid, fileName);
  }

  public Media save(Media media) {
    Media registered = mediaRepository.save(media);
    return registered;
  }

  public void delete(Long id) {
    mediaRepository.delete(id);
  }

}
