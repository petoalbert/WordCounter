package io.github.petoalbert.application

import io.github.petoalbert.application.JsonProcessor.{Config, ParsedEvent}
import io.github.petoalbert.domain.{Event, EventType}
import io.github.vigoo.prox.ProxError
import io.github.vigoo.prox.zstream._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.json._
import zio.logging.{Logger, Logging}
import zio.stream.ZTransducer
import zio.{Exit, Has, ZIO, ZLayer}

import java.io.IOException
import java.time.Instant

trait JsonProcessor {
  def startProcessing(): ZIO[Any, IOException, Unit]
}

class LiveJsonProcessor(
  logger: Logger[String],
  blocking: zio.blocking.Blocking.Service,
  clock: Clock.Service,
  config: Config,
  registry: WordCountRegistry
) extends JsonProcessor {

  implicit val eventTypeDecoder: JsonDecoder[EventType] = JsonDecoder.string.map(EventType)
  implicit val timestampDecoder: JsonDecoder[Instant]   = JsonDecoder.long.map(Instant.ofEpochSecond)
  implicit val eventDecoder: JsonDecoder[ParsedEvent]   = DeriveJsonDecoder.gen[ParsedEvent]

  private val proc = Process(config.command, config.args.getOrElse(List.empty)).drainOutput(
    (ZTransducer.utf8Decode >>> ZTransducer.splitLines).mapM(processLine)
  )

  private implicit val runner: ProcessRunner[JVMProcessInfo] = new JVMProcessRunner

  override def startProcessing(): ZIO[Any, Nothing, Unit] =
    proc
      .run()
      .run
      .tap(logExit)
      .delay(5.second)
      .forever // I assume the program should never stop, so we can only restart with a delay in this case
      .provideLayer(ZLayer.succeed(blocking) ++ ZLayer.succeed(clock) ++ ZLayer.succeed(logger))

  private def logExit(exit: Exit[ProxError, ProcessResult[Any, Any]]): ZIO[Any, Nothing, Unit] =
    exit match {
      case Exit.Success(v)     =>
        logger.warn(s"""Process has exited with "${v.exitCode}". Retrying...""")
      case Exit.Failure(cause) =>
        cause.failureOption match {
          case Some(value) =>
            val throwable = value.toThrowable
            logger.error(s"Process has failed with ${throwable.getMessage}. Retrying...")
          case None        =>
            logger.error(s"Process has failed. Retrying...")

        }
    }

  private def processLine(s: String): ZIO[Any, Nothing, Unit] =
    ZIO
      .fromEither(s.fromJson[ParsedEvent])
      .map(e => Event(e.eventType, e.data.trim.split("[ ,!.]+").length, e.timestamp))
      .flatMap(registry.addEvent)
      .catchAll(_ => ZIO.unit)
}

object JsonProcessor {
  case class Config(command: String, args: Option[List[String]])

  val live: ZLayer[Logging with Blocking with Clock with Has[Config] with Has[WordCountRegistry], Nothing, Has[JsonProcessor]] =
    ZLayer.fromServices[Logger[String], Blocking.Service, Clock.Service, Config, WordCountRegistry, JsonProcessor](
      (logger, blocking, clock, config, registry) => new LiveJsonProcessor(logger, blocking, clock, config, registry)
    )

  val startProcessing: ZIO[Has[JsonProcessor], IOException, Unit] =
    ZIO.accessM[Has[JsonProcessor]](_.get.startProcessing())

  case class ParsedEvent(@jsonField("event_type") eventType: EventType, data: String, timestamp: Instant)
}
