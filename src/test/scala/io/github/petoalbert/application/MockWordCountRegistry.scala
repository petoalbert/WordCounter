package io.github.petoalbert.application

import io.github.petoalbert.domain.{Event, EventType, WordCount}
import zio.ZIO

case class MockWordCountRegistry(wordCounts: Map[EventType, Int]) extends WordCountRegistry {
  override val getWordCounts: ZIO[Any, Nothing, List[WordCount]] =
    ZIO.succeed(wordCounts.map { case (eventType, count) => WordCount(eventType, count) }.toList)

  override def getWordCount(eventType: EventType): ZIO[Any, Nothing, Option[WordCount]] =
    ZIO.succeed(wordCounts.get(eventType).map(count => WordCount(eventType, count)))

  override def addEvent(event: Event): ZIO[Any, Nothing, Unit] = ???
}
