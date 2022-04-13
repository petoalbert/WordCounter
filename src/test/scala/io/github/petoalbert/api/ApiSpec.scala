package io.github.petoalbert.api

import akka.http.interop.HttpServer
import akka.http.scaladsl.model.StatusCodes
import io.github.petoalbert.application.{MockWordCountRegistry, WordCountRegistry}
import io.github.petoalbert.domain.{EventType, WordCount}
import io.github.petoalbert.interop.akka.ZioRouteTest
import zio.blocking.{Blocking, effectBlocking}
import zio.clock.Clock
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{LogAnnotation, Logging}
import zio.test.Assertion._
import zio.test._
import zio.{Has, ULayer, ZLayer}

object ApiSpec extends ZioRouteTest with JsonSupport {
  private val loggingLayer: ULayer[Logging] = Slf4jLogger.make { (context, message) =>
    val logFormat     = "[correlation-id = %s] %s"
    val correlationId = LogAnnotation.CorrelationId.render(
      context.get(LogAnnotation.CorrelationId)
    )
    logFormat.format(correlationId, message)
  }
  val wordCountRegistryLayer: ZLayer[Any, Nothing, Has[WordCountRegistry]] = ZLayer.succeed(
    MockWordCountRegistry(Map(
      EventType("a") -> 3,
      EventType("b") -> 4
    ))
  )

  val apiLayer = (
    wordCountRegistryLayer ++
      loggingLayer ++
      ZLayer.succeed(HttpServer.Config("localhost", 8080))
    ) >>> Api.live.passthrough

  private val env = apiLayer ++ Blocking.live ++ Clock.live ++ Annotations.live

  private def specs: Spec[Blocking with Has[Api], TestFailure[Throwable], TestSuccess] =
    suite("Api")(
      testM("Returns 404 if event type is not present in window") {
        for {
          routes      <- Api.routes
          request      = Get("/words/notexisting")
          resultCheck <- effectBlocking(request ~> routes ~> check {
            // Here and in other tests we have to evaluate response on the spot before passing anything to `assert`.
            // This is due to really tricky nature of how `check` works with the result (no simple workaround found so far)
            val theStatus = status
            assert(theStatus)(equalTo(StatusCodes.NotFound))
          })
        } yield resultCheck
      },
      testM("Returns all word counts") {
        for {
          routes      <- Api.routes
          request      = Get("/words")
          resultCheck <- effectBlocking(request ~> routes ~> check {
            // Here and in other tests we have to evaluate response on the spot before passing anything to `assert`.
            // This is due to really tricky nature of how `check` works with the result (no simple workaround found so far)
            val theStatus = status
            val theBody   = entityAs[List[WordCount]]
            assert(theStatus)(equalTo(StatusCodes.OK)) &&
              assert(theBody)(
                equalTo(
                  List(
                    WordCount(EventType("a"), 3),
                    WordCount(EventType("b"), 4)
                  )
                )
              )
          })
        } yield resultCheck
      },
      testM("Returns word count for event type") {
        for {
          routes      <- Api.routes
          request      = Get("/words/a")
          resultCheck <- effectBlocking(request ~> routes ~> check {
            // Here and in other tests we have to evaluate response on the spot before passing anything to `assert`.
            // This is due to really tricky nature of how `check` works with the result (no simple workaround found so far)
            val theStatus = status
            val theBody   = entityAs[WordCount]
            assert(theStatus)(equalTo(StatusCodes.OK)) &&
              assert(theBody)(
                equalTo(
                  WordCount(EventType("a"), 3)
                )
              )
          })
        } yield resultCheck
      }

    )

  def spec = specs.provideLayer(env)
}

