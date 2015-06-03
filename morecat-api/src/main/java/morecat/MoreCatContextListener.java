package morecat;

import javax.annotation.Resource;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.sql.DataSource;

/**
 * @author Yoshimasa Tanabe
 */
@WebListener
public class MoreCatContextListener implements ServletContextListener {

  @Resource(mappedName = "java:jboss/datasources/morecatDS")
  private DataSource ds;

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    MoreCatLogger.LOGGER.starting(Version.getVersion());

    migrateDatabase();
  }

  private void migrateDatabase() {
//    Flyway flyway = new Flyway();
//    flyway.setDataSource(ds);
//    flyway.migrate();
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {}

}
