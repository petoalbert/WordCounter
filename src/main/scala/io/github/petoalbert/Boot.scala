package io.github.petoalbert

import akka.actor.ActorSystem
import akka.http.interop._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import zio.config.typesafe.TypesafeConfig
import zio.console._
import zio.logging._
import zio.logging.slf4j._
import zio._
import io.github.petoalbert.api._
import io.github.petoalbert.application.WordCountRegistry
import io.github.petoalbert.config.AppConfig

object Boot extends App {

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    ZIO(ConfigFactory.load.resolve)
      .flatMap(rawConfig => program.provideCustomLayer(prepareEnvironment(rawConfig)))
      .exitCode

  private val program: RIO[HttpServer with ZEnv, Unit] = {
    val startHttpServer =
      HttpServer.start.tapM(_ => putStrLn("Server online."))

    startHttpServer.useForever
  }

  private def prepareEnvironment(rawConfig: Config): TaskLayer[HttpServer] = {
    val configLayer = TypesafeConfig.fromTypesafeConfig(rawConfig, AppConfig.descriptor)

    // narrowing down to the required part of the config to ensure separation of concerns
    val apiConfigLayer = configLayer.map(c => Has(c.get.api))

    val actorSystemLayer: TaskLayer[Has[ActorSystem]] = ZLayer.fromManaged {
      ZManaged.make(ZIO(ActorSystem("githubrank-system")))(s => ZIO.fromFuture(_ => s.terminate()).either)
    }

    val loggingLayer: ULayer[Logging] = Slf4jLogger.make { (context, message) =>
      val logFormat     = "[correlation-id = %s] %s"
      val correlationId = LogAnnotation.CorrelationId.render(
        context.get(LogAnnotation.CorrelationId)
      )
      logFormat.format(correlationId, message)
    }

    val applicationLayer: ZLayer[Any, Throwable, Has[WordCountRegistry]] =
      WordCountRegistry.mock

    val apiLayer: TaskLayer[Has[Api]] =
      (apiConfigLayer ++ applicationLayer ++ actorSystemLayer ++ loggingLayer) >>> Api.live

    val routesLayer: URLayer[Has[Api], Has[Route]] =
      ZLayer.fromService(_.routes)

    val serverEnv: TaskLayer[HttpServer] =
      (actorSystemLayer ++ apiConfigLayer ++ (apiLayer >>> routesLayer)) >>> HttpServer.live

    serverEnv
  }
}
