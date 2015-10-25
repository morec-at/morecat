package morecat.admin.web.entry;

import javax.enterprise.context.RequestScoped;
import javax.inject.Named;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Yoshimasa Tanabe
 */
@RequestScoped
@Named
public class EntryViewController {

  public String say() {
    System.out.println("#####################");
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8080/entries").request(MediaType.APPLICATION_JSON).get();
    String s = response.readEntity(String.class);
    System.out.println(s);
    System.out.println("#####################");
    return "Hello, MoreCat Admin Web!";
  }

}
