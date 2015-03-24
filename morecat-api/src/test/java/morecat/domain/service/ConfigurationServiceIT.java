package morecat.domain.service;

import morecat.MoreCatDeployment;
import morecat.domain.model.Configuration;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import static org.hamcrest.CoreMatchers.*;

@RunWith(Arquillian.class)
public class ConfigurationServiceIT {

  @Deployment
  public static WebArchive deployment() {
    WebArchive testWar = MoreCatDeployment.deployment();
//    System.out.println(testWar.toString(true));
    return testWar;
  }

  @Inject
  private ConfigurationService sut;

  @Test
  public void should_get_only_a_record() throws Exception {
    // Exercise
    Configuration configuration = sut.find();
    // Verify
    Assert.assertThat(configuration.getBlogName(), is("default_name"));
    Assert.assertThat(configuration.getBlogDescription(), is("default_description"));
  }

}