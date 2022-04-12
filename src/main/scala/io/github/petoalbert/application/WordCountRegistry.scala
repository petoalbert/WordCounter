package io.github.petoalbert.application

import io.github.petoalbert.domain.{Event, EventType, WordCount}
import zio.clock.Clock
import zio.stm.{TMap, TQueue, ZSTM}
import zio.{Has, ZIO, ZLayer}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait WordCountRegistry {
  val getWordCounts: ZIO[Any, Nothing, List[WordCount]]
  def addEvent(event: Event): ZIO[Any, Nothing, Unit]
}

class LiveWordCountRegistry(
  timeWindow: FiniteDuration,
  clock: Clock.Service,
  queue: TQueue[Event],
  wordCounts: TMap[EventType, Int]
) extends WordCountRegistry {
  override val getWordCounts: ZIO[Any, Nothing, List[WordCount]] =
    clock.currentTime(TimeUnit.MILLISECONDS).flatMap { time =>
      (removeTimedOut(time) >>> wordCounts.toList).commit.map(_.map { case (eventType, count) =>
        WordCount(eventType, count)
      })
    }

  override def addEvent(event: Event): ZIO[Any, Nothing, Unit] =
    clock.currentTime(TimeUnit.MILLISECONDS).flatMap { time =>
      (removeTimedOut(time) >>> addToMap(event)).commit
    }

  private def removeFromMap(event: Event): ZSTM[Any, Nothing, Unit] =
    for {
      count <- wordCounts.get(event.eventType)
      _     <- count match {
                 case Some(count) if count == 0 =>
                   wordCounts.delete(event.eventType)
                 case Some(count)               =>
                   wordCounts.put(event.eventType, count - event.words)
                 case _                         =>
                   ZSTM.unit
               }
    } yield ()

  private def addToMap(event: Event): ZSTM[Any, Nothing, Unit] =
    for {
      count <- wordCounts.get(event.eventType)
      _     <- count match {
                 case Some(count) => wordCounts.put(event.eventType, count + event.words)
                 case None        => wordCounts.put(event.eventType, event.words)
               }
    } yield ()

  private def removeTimedOut(currentTime: Long): ZSTM[Any, Nothing, Unit] =
    for {
      event <- queue.peekOption
      _     <- event match {
                 case Some(value) if value.timestamp + timeWindow.toMillis < currentTime =>
                   queue.take >>> (removeFromMap(value) >>> removeTimedOut(currentTime))
                 case _                                                                  =>
                   ZSTM.unit
               }
    } yield ()

}

object WordCountRegistry {
  val live: ZLayer[Clock, Nothing, Has[WordCountRegistry]] =
    ZLayer.fromServiceM(clock =>
      (for {
        queue <- TQueue.unbounded[Event]
        map   <- TMap.make[EventType, Int]()
      } yield new LiveWordCountRegistry(1.second, clock, queue, map)).commit
    )
}
