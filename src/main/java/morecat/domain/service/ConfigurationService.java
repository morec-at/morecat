package morecat.domain.service;

import morecat.domain.model.Configuration;
import morecat.domain.repository.ConfigurationRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

/**
* @author Yoshimasa Tanabe
*/
@ApplicationScoped
public class ConfigurationService {

  @Inject
  private ConfigurationRepository configurationRepository;

  public Configuration find() {
    return configurationRepository.get();
  }

  @Transactional
  public Configuration save(Configuration configuration) {
    return configurationRepository.save(configuration);
  }

}
