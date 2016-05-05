package morecat.domain.service;

import morecat.MoreCatContainer;
import morecat.MoreCatDeployment;
import morecat.domain.model.Configuration;
import morecat.domain.repository.ConfigurationRepository;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.ContainerFactory;
import org.wildfly.swarm.container.Container;

import javax.inject.Inject;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class ConfigurationServiceIT implements ContainerFactory {

  @Deployment
  public static Archive deployment() throws Exception {
    return MoreCatDeployment.deployment();
  }

  @Override
  public Container newContainer(String... args) throws Exception {
    return MoreCatContainer.newContainer();
  }

  @Inject
  private ConfigurationService sut;

  @Inject
  private ConfigurationRepository configurationRepository;

  @Before
  public void setUp() throws Exception {
    configurationRepository.deleteAll();

    Configuration configuration = new Configuration("default_name", "default_description", false);
    configurationRepository.save(configuration);
  }

  @Test
  public void should_get_only_a_record() throws Exception {
    // Exercise
    Configuration configuration = sut.find();
    // Verify
    assertThat(configuration, is(notNullValue()));
    assertThat(configuration.getBlogName(), is("default_name"));
    assertThat(configuration.getBlogDescription(), is("default_description"));
    assertThat(configuration.isPublicity(), is(false));
  }

  @Test
  public void should_change_configuration_status() throws Exception {
    // Setup
    Configuration configuration = sut.find();
    configuration.setBlogName("another_name");
    configuration.setBlogDescription("another_description");
    configuration.setPublicity(true);
    // Exercise
    sut.save(configuration);
    // Verify
    Configuration updated = sut.find();
    assertThat(updated, is(notNullValue()));
    assertThat(updated.getBlogName(), is("another_name"));
    assertThat(updated.getBlogDescription(), is("another_description"));
    assertThat(updated.isPublicity(), is(true));
  }

}