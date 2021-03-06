package io.github.petoalbert.application

import cats.Order
import cats.collections.Heap
import io.github.petoalbert.application.WordCountRegistry.Config
import io.github.petoalbert.domain.{Event, EventType, WordCount}
import zio.clock.Clock
import zio.duration.Duration
import zio.stm.{TMap, TRef, ZSTM}
import zio.{Has, ZIO, ZLayer}

import java.time.Instant

trait WordCountRegistry {
  val getWordCounts: ZIO[Any, Nothing, List[WordCount]]
  def getWordCount(eventType: EventType): ZIO[Any, Nothing, Option[WordCount]]
  def addEvent(event: Event): ZIO[Any, Nothing, Unit]
}

/*
 * Keeps track of word counts by event type in a time window.
 */
class LiveWordCountRegistry(
  config: Config,
  clock: Clock.Service,
  heap: TRef[Heap[Event]],
  wordCounts: TMap[EventType, Int]
) extends WordCountRegistry {

  implicit val eventOrder: Order[Event] = Order.from((a, b) => a.timestamp.compareTo(b.timestamp))

  private val removeTimedOut: ZIO[Any, Nothing, Unit] =
    clock.instant.flatMap(instant => removeTimedOut(instant))

  private def removeTimedOut(currentTime: Instant): ZIO[Any, Nothing, Unit] = {
    val removed = for {
      event   <- heap.get.map(_.getMin)
      removed <- event match {
                   case Some(value) if (value.timestamp plus config.timeWindow) isBefore currentTime =>
                     heap.update(_.remove) >>> (removeFromMap(value) >>> ZSTM.succeed(true))
                   case _                                                                            =>
                     ZSTM.succeed(false)
                 }
    } yield removed

    removed.commit.flatMap(removed =>
      if (removed) {
        removeTimedOut(currentTime)
      } else {
        ZIO.unit
      }
    )
  }

  override val getWordCounts: ZIO[Any, Nothing, List[WordCount]] =
    removeTimedOut >>> wordCounts.toList.commit.map(_.map { case (eventType, count) =>
      WordCount(eventType, count)
    })

  override def addEvent(event: Event): ZIO[Any, Nothing, Unit] =
    removeTimedOut >>> (addToMap(event) >>> heap.update(_.add(event))).commit

  def getWordCount(eventType: EventType): ZIO[Any, Nothing, Option[WordCount]] =
    removeTimedOut >>> wordCounts
      .get(eventType)
      .commit
      .map(_.map(count => WordCount(eventType, count)))

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

}

object WordCountRegistry {
  case class Config(timeWindow: Duration)

  def live: ZLayer[Clock with Has[Config], Nothing, Has[WordCountRegistry]] =
    ZLayer.fromServicesM[Clock.Service, Config, Any, Nothing, WordCountRegistry]((clock, config) =>
      (for {
        heap <- TRef.make(Heap.empty[Event])
        map  <- TMap.make[EventType, Int]()
      } yield new LiveWordCountRegistry(config, clock, heap, map)).commit
    )

  val getWordCounts: ZIO[Has[WordCountRegistry], Nothing, List[WordCount]] =
    ZIO.accessM[Has[WordCountRegistry]](_.get.getWordCounts)

  def addEvent(event: Event): ZIO[Has[WordCountRegistry], Nothing, Unit] =
    ZIO.accessM[Has[WordCountRegistry]](_.get.addEvent(event))
}
