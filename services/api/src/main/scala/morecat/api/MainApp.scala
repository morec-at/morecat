package morecat.api

import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.html.{html => html5, _}

object MainApp extends ZIOAppDefault {



  //
  // Application HTML-Layout 
  //

  def withContentHtml(contentTitle:zio.http.html.Html)(content: zio.http.html.Html) = 
    html5(
      head(
        title("ZIO Http"), 
        link(relAttr := "stylesheet", href := "/assets/styles.css"), 
        meta(charsetAttr := "utf-8")
      ), 
      body(
        header(
          a(href := "/", img(srcAttr := "/assets/zio.png")),
          nav(
            ul(
              li(a(href := "/", "Homepage")), 
              li(a(href := "https://zio.dev/zio-http/", "Documentation")), 
              li(a(href := "https://www.github.com/zio/zio-http/", "Github")), 
              li(a(href := "https://github.com/zio/zio-http/tree/main/zio-http-example/src/main/scala/example", "Github Examples")),
            )
          ),
        ),
        zio.http.html.main(
          div(
            contentTitle,
            content
          )
        )
      )
    ) 



  //
  // Web Assets
  //

  def makeWebAssets: Http[Any, Throwable, Request, Response] = 
    Http.collectHttp[Request] {
      case Method.GET -> !! / "assets" / asset =>
        Http.fromResource(asset)
    }



  //
  // Web Application
  //

  def makeWebApp:App[Any] = 
    Http.collect[Request] {

      case Method.GET -> !! => 
        Response.html(
          withContentHtml(h2(styleAttr := List("border" -> "none"), "You're running on ZIO Http!")) {
            div(
              p("""This starter project may help you getting started with your own web application, morecat-api:0.0.1 """),
              p("""You can easily inspect and modify the code, it's only a single file"""),
              h3("Project examples"),
              ul(
                li(a(href := "/sbt-revolver", "Hot-Reload changes with SBT-Revolver")),
                li(a(href := "/docker", "Dockerize this application")),
                li(a(href := "/webapi-with-endpoints", """WebAPI with Endpoints""")),                
                li(a(href := "/show-internal-server-error", """Customized "Internal Server Error"""")),
                li(a(href := "/show-not-found", """Customized "Not Found""""))
              ), 
              a(classAttr := List("next"), href := "/sbt-revolver", "Start")
            )            
          }
        )

      case Method.GET -> !! / "sbt-revolver" => 
        Response.html(
          withContentHtml(h2("Hot-Reload changes with SBT-Revolver")) {
            div(
              ul(
                li(h4("Start your app with SBT-Revolver")), 
                li(pre("sbt:morecat-api> ~reStart")),
              ), 
              p("""Your app will be reloaded whenever your source code changes"""),
              p("""Besides SBT-Revolver this starter project also contains other useful <a href="https://zio.dev/zio-http/setup#includes">SBT plugins</a>"""),
              a(classAttr := List("next"), href := "/docker", "Next")
            )            
          }
        )        

      case Method.GET -> !! / "docker" => 
        Response.html(
          withContentHtml(h2("Dockerize this application")) {
            div(
              ul(
                li(h4("Build a local docker image directly from SBT")), 
                li(pre("sbt:morecat-api> Docker / publishLocal")), 
                li(h4("Then run a container")), 
                li(pre("$ docker run -p 8080:8080 morecat-api:0.0.1")),
                li(h4("""Then point a browser at <a href="http://localhost:8080">http://localhost:8080</a>""")),
                li(h4("Customize your Dockerfile")), 
                li(
                  p(
                    s"""Lookup <a href="https://sbt-native-packager.readthedocs.io/en/stable/formats/docker.html#settings">the docs</a> for the SBT Native Packager Plugin and learn how to customize your Dockerfile"""
                  )
                ),
              ), 
              a(classAttr := List("next"), href := "/webapi-with-endpoints", "Next")
            )            
          }
        )

      case Method.GET -> !! / "webapi-with-endpoints" =>
        Response.html(
          withContentHtml(h2("WebAPI with Endpoints")) {
            div(
              ul(
                li(h4("Describe your API's endpoints")), 
                li(pre(
"""
Endpoint
  .get("api" / "users" / int("userId"))
  .query(queryBool("show-details"))
  .out[String]
""")), 
                li(
                  p(
                    s"""Endpoints serve as a single source of truth for your whole Http API"""
                  ), 
                  p(
                    """Learn more about <a href="https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/EndpointExamples.scala">ZIO Endpoints</a>"""
                  )
                )
              ), 
              a(classAttr := List("next"), href := "/show-internal-server-error", "Next")
            )            
          }
        )          


      case Method.GET -> !! / "show-internal-server-error" =>
        throw new Exception("internal server error")
    }


  //
  // Web API
  //

  def makeWebAPI:App[Any] = {
    import zio.http.codec.HttpCodec._
    Endpoint
      .get("api" / "users" / int("userId"))
      .query(queryBool("show-details"))
      .out[String]
      .implement { 
        case (userId, showDetails) => 
          ZIO.succeed("user " + userId + " with details? " + showDetails)
      }.toApp
  }



  //
  // Render a Http Not Found
  //

  def makeNotFound = 
    Http.collect[Request] {
      case req => 
        Response.html(
          withContentHtml(h2("""Customized <br />Http Not Found"""))(
            div(
              div(
                p(s"""Sorry, your requested page "${req.path.encode}" does not exist."""), 
                p("This is a customized page for any non-existing endpoints in your application"),
                a(href := "/and-another-not-found", "Try one more")
              )
            )
          ), 
          Status.NotFound
        )
    }



  //
  // Compose your separate http apps to a larger http app 
  //

  val routes = (
    makeWebApp    ++ 
    makeWebAPI    ++ 
    makeWebAssets ++ 
    makeNotFound
  )
    .catchAllCauseZIO(_ => ZIO.succeed(
        Response.html(
          withContentHtml(h2("""Customized <br />Http Internal Server Error"""))(
            div(
              div(
                p("Sorry, we did not expect this failure."), 
                p("This is a customized page for any errors that may happen unexpectedly"),
              ),           
              a(classAttr := List("next"), href := "/show-not-found", "Next")
            )
          ), 
          Status.InternalServerError
        )
      )
    )



  //
  // Start your server
  //

  override val run = {
    Console.printLine("please visit http://localhost:8080") *> 
    Server.serve(routes).provide(Server.default)
  }
}
