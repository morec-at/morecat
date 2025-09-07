package morecat.api

import zio.*
import zio.http.*

object MainApp extends ZIOAppDefault {
  val routes = Routes(
    Method.GET / Root -> handler(Response.text("Hello, this is morecat"))
  )

  override val run = Server.serve(routes).provide(Server.default)
}
