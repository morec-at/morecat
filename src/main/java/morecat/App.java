package morecat;

public class App {

  public static void main(String... args) throws Exception {
    MoreCatContainer.newContainer(args)
      .start()
      .deploy(MoreCatDeployment.deployment());
  }

}
