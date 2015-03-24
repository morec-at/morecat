package morecat;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author Yoshimasa Tanabe
 */
@MessageLogger(projectCode = "MORECAT")
public interface MoreCatLogger extends BasicLogger {

  MoreCatLogger LOGGER = Logger.getMessageLogger(MoreCatLogger.class, MoreCatLogger.class.getPackage().getName());

  @LogMessage(level = Logger.Level.INFO)
  @Message(id = 1, value = "MoreCat %s starting")
  void starting(String version);

}
