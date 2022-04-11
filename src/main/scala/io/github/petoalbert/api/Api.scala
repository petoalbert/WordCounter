package io.github.petoalbert.api

import akka.http.interop._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.github.petoalbert.application.WordCountRegistry
import zio._
import zio.logging._

trait Api {
  def routes: Route
}

object Api {
  val live: ZLayer[Has[HttpServer.Config] with Has[WordCountRegistry] with Logging, Nothing, Has[Api]] =
    ZLayer.fromFunction(env =>
      new Api with JsonSupport with ZIOSupport {

        def routes: Route = itemRoute

        val itemRoute: Route =
          path("words") {
            get {
              complete(env.get[WordCountRegistry].getWordCounts)
            }
          }
      }
    )

  // accessors
  val routes: URIO[Has[Api], Route] = ZIO.access[Has[Api]](a => Route.seal(a.get.routes))
}
