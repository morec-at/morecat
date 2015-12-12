package morecat;

/**
 * @author Yoshimasa Tanabe
 */
public class App {

  public static void main(String[] args) throws Exception {
    MoreCatContainer.newContainer()
      .start()
      .deploy(MoreCatDeployment.deployment());
  }

}
