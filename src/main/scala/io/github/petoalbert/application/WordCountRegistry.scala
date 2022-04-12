package io.github.petoalbert.application

import io.github.petoalbert.domain.{Event, EventType, WordCount}
import zio.clock.Clock
import zio.duration.Duration
import zio.stm.{TMap, TQueue, ZSTM}
import zio.{Has, ZIO, ZLayer}

import java.time.Instant

trait WordCountRegistry {
  val getWordCounts: ZIO[Any, Nothing, List[WordCount]]
  def addEvent(event: Event): ZIO[Any, Nothing, Unit]
}

class LiveWordCountRegistry(
  timeWindow: Duration,
  clock: Clock.Service,
  queue: TQueue[Event],
  wordCounts: TMap[EventType, Int]
) extends WordCountRegistry {
  override val getWordCounts: ZIO[Any, Nothing, List[WordCount]] =
    clock.instant.flatMap { time =>
      (removeTimedOut(time) >>> wordCounts.toList).commit.map(_.map { case (eventType, count) =>
        WordCount(eventType, count)
      })
    }

  override def addEvent(event: Event): ZIO[Any, Nothing, Unit] =
    clock.instant.flatMap { time =>
      (removeTimedOut(time) >>> addToMap(event) >>> queue.offer(event)).commit
    }

  private def removeFromMap(event: Event): ZSTM[Any, Nothing, Unit] =
    for {
      count <- wordCounts.get(event.eventType)
      _     <- count match {
                 case Some(count) if count == event.words =>
                   wordCounts.delete(event.eventType)
                 case Some(count)                         =>
                   wordCounts.put(event.eventType, count - event.words)
                 case _                                   =>
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

  private def removeTimedOut(currentTime: Instant): ZSTM[Any, Nothing, Unit] =
    for {
      event <- queue.peekOption
      _     <- event match {
                 case Some(value) if (value.timestamp plus timeWindow) isBefore currentTime =>
                   queue.take >>> (removeFromMap(value) >>> removeTimedOut(currentTime))
                 case _                                                                     =>
                   ZSTM.unit
               }
    } yield ()

}

object WordCountRegistry {
  def live(window: Duration): ZLayer[Clock, Nothing, Has[WordCountRegistry]] =
    ZLayer.fromServiceM[Clock.Service, Any, Nothing, WordCountRegistry](clock =>
      (for {
        queue <- TQueue.unbounded[Event]
        map   <- TMap.make[EventType, Int]()
      } yield new LiveWordCountRegistry(window, clock, queue, map)).commit
    )

  val getWordCounts: ZIO[Has[WordCountRegistry], Nothing, List[WordCount]] =
    ZIO.accessM[Has[WordCountRegistry]](_.get.getWordCounts)

  def addEvent(event: Event): ZIO[Has[WordCountRegistry], Nothing, Unit] =
    ZIO.accessM[Has[WordCountRegistry]](_.get.addEvent(event))
}
