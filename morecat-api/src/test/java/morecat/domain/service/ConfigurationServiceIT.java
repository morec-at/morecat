package morecat.domain.service;

import morecat.MoreCatDeployment;
import morecat.domain.model.Configuration;
import morecat.domain.repository.ConfigurationRepository;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import static org.hamcrest.CoreMatchers.*;

@RunWith(Arquillian.class)
public class ConfigurationServiceIT {

  @Deployment
  public static JavaArchive deployment() {
    JavaArchive testJar = MoreCatDeployment.deployment();
//    System.out.println(testJar.toString(true));
    return testJar;
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
    Assert.assertThat(configuration, is(notNullValue()));
    Assert.assertThat(configuration.getBlogName(), is("default_name"));
    Assert.assertThat(configuration.getBlogDescription(), is("default_description"));
    Assert.assertThat(configuration.isPublicity(), is(false));
  }

  @Test
  public void should_change_configuration_status() throws Exception {
    // Setup
    Configuration configuration = sut.find();
    configuration.setBlogName("another_name");
    configuration.setBlogDescription("another_description");
    configuration.setPublicity(true);
    // Exercise
    sut.update(configuration);
    // Verify
    Configuration updated = sut.find();
    Assert.assertThat(updated, is(notNullValue()));
    Assert.assertThat(updated.getBlogName(), is("another_name"));
    Assert.assertThat(updated.getBlogDescription(), is("another_description"));
    Assert.assertThat(updated.isPublicity(), is(true));
  }
}