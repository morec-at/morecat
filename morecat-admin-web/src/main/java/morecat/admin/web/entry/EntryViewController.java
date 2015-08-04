package morecat.admin.web.entry;

import javax.enterprise.inject.Model;

/**
 * @author Yoshimasa Tanabe
 */
@Model
public class EntryViewController {

  public String say() {
    return "Hello, MoreCat Admin Web!";
  }

}
