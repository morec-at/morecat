package morecat;

import org.wildfly.swarm.container.Container;

public class MoreCatContainer {

  private static final String DATASOURCE_NAME = "morecatDS";

  public static Container newContainer() throws Exception {
    Container container = new Container();

    MoreCatConfiguration configuration = new MoreCatConfiguration(container);

    container
        .fraction(configuration.datasourcesFraction(DATASOURCE_NAME))
        .fraction(configuration.jpaFraction(DATASOURCE_NAME));

    return container;
  }

}
