package morecat;

import morecat.domain.model.Configuration;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "MORECAT")
public interface MoreCatLogger extends BasicLogger {

  MoreCatLogger LOGGER = Logger.getMessageLogger(MoreCatLogger.class, MoreCatLogger.class.getPackage().getName());

  @LogMessage(level = Logger.Level.INFO)
  @Message(id = 1, value = "MoreCat %s starting")
  void starting(String version);

  @LogMessage(level = Logger.Level.INFO)
  @Message(id = 2, value = "Updated entry %d/%d/%d/%s by %s")
  void updateEntry(int year, int month, int day, String permalink, String author);

  @LogMessage(level = Logger.Level.INFO)
  @Message(id = 3, value = "Deleted entry %d/%d/%d/%s by %s")
  void deleteEntry(int year, int month, int day, String permalink, String author);

  @LogMessage(level = Logger.Level.INFO)
  @Message(id = 4, value = "Uploaded media %s/%s by %s")
  void uploadMedia(String uuid, String fileName, String author);

  @LogMessage(level = Logger.Level.INFO)
  @Message(id = 5, value = "Deleted media %s/%s by %s")
  void deleteMedia(String uuid, String fileName, String author);

  @LogMessage(level = Logger.Level.INFO)
  @Message(id = 6, value = "Updated configuration to %s by %s")
  void updateConfiguration(Configuration updated, String admin);

}
