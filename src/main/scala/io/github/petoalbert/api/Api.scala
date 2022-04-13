package io.github.petoalbert.api

import akka.http.interop._
import io.github.petoalbert.domain.NotFound
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.github.petoalbert.application.WordCountRegistry
import io.github.petoalbert.domain.{DomainError, EventType}
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

        implicit val domainErrorResponse: ErrorResponse[DomainError] = { case NotFound =>
          HttpResponse(StatusCodes.NotFound)
        }

        val itemRoute: Route =
          path("words") {
            get {
              complete(env.get[WordCountRegistry].getWordCounts)
            }
          } ~ path("words" / Segment) { eventType =>
            get {
              complete(
                env
                  .get[WordCountRegistry]
                  .getWordCount(EventType(eventType))
                  .flatMap(res => ZIO.fromOption(res))
                  .mapError[DomainError](_ => NotFound)
              )
            }
          }
      }
    )

  // accessors
  val routes: URIO[Has[Api], Route] = ZIO.access[Has[Api]](a => Route.seal(a.get.routes))
}
