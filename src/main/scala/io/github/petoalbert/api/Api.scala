package io.github.petoalbert.api

import akka.http.interop._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.github.petoalbert.application.ApplicationService
import io.github.petoalbert.domain.{EventType, WordCount}
import zio._
import zio.logging._

trait Api {
  def routes: Route
}

object Api {
  val live: ZLayer[Has[HttpServer.Config] with Has[ApplicationService] with Logging, Nothing, Has[Api]] =
    ZLayer.fromFunction(_ =>
      new Api with JsonSupport with ZIOSupport {

        def routes: Route = itemRoute

        val itemRoute: Route =
          path("words") {
            get {
              complete(List(WordCount(EventType("one"), 3), WordCount(EventType("two"), 222)))
            }
          }
      }
    )

  // accessors
  val routes: URIO[Has[Api], Route] = ZIO.access[Has[Api]](a => Route.seal(a.get.routes))
}
