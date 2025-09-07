package morecat.domain.repository;

import morecat.domain.model.Configuration;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfigurationRepository extends BaseRepository<Configuration> {

  @Override
  protected Class<Configuration> getEntityClass() {
    return Configuration.class;
  }

  public Configuration get() {
    return getSingleResult((builder, query, root) -> query.select(root));
  }

}
