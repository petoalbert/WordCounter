package io.github.petoalbert.application

import io.github.petoalbert.domain.{Event, EventType, WordCount}
import zio.{Has, ZLayer}
import zio.clock.Clock
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.environment.{TestClock, TestEnvironment}
import zio.test._

object WordCountRegistrySpecs extends DefaultRunnableSpec {

  def specs: ZSpec[Has[WordCountRegistry] with TestClock with Clock, Failure] =
    suite("word count registry")(
      testM("aggregates events by event type")(
        for {
          now <- zio.clock.instant
          _ <- WordCountRegistry.addEvent(Event(EventType("a"), 2, now))
          _ <- WordCountRegistry.addEvent(Event(EventType("a"), 3, now))
          _ <- WordCountRegistry.addEvent(Event(EventType("b"), 10, now))
          res <- WordCountRegistry.getWordCounts
        } yield assert(res)(
          contains(WordCount(EventType("a"), 5)) &&
            contains(WordCount(EventType("b"), 10))
        )
      ),
      testM("removes event type group if no events are in time window")(
        for {
          now <- zio.clock.instant
          _ <- WordCountRegistry.addEvent(Event(EventType("a"), 2, now))
          _ <- WordCountRegistry.addEvent(Event(EventType("a"), 3, now plus 2.seconds))
          _ <- TestClock.adjust(10.second)
          res <- WordCountRegistry.getWordCounts
        } yield assert(res)(isEmpty)
      ),
      testM("removes events from group outside the time window")(
        for {
          now <- zio.clock.instant
          _ <- WordCountRegistry.addEvent(Event(EventType("a"), 2, now))
          _ <- WordCountRegistry.addEvent(Event(EventType("a"), 3, now plus 2.seconds))
          _ <- TestClock.adjust(2.second)
          res <- WordCountRegistry.getWordCounts
        } yield assert(res)(equalTo(List(WordCount(EventType("a"), 3))))
      )
    )

  val config: ZLayer[Any, Nothing, Has[WordCountRegistry.Config]] =
    ZLayer.succeed(WordCountRegistry.Config(1.second))

  def spec: ZSpec[TestEnvironment, Failure] = {
    specs.provideCustomLayer(
      (ZLayer.identity[Clock] ++ config) >>> WordCountRegistry.live
    )
  }

}
