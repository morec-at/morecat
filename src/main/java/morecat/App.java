package morecat;

public class App {

  public static void main(String[] args) throws Exception {
    MoreCatContainer.newContainer()
      .start()
      .deploy(MoreCatDeployment.deployment());
  }

}
