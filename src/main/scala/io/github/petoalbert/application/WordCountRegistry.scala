package io.github.petoalbert.application

import io.github.petoalbert.domain.{EventType, WordCount}
import zio.{Has, ZIO, ZLayer}

trait WordCountRegistry {
  val getWordCounts: ZIO[Any, Nothing, List[WordCount]]
  def addWordCount(words: WordCount): ZIO[Any, Nothing, Unit]
}

class MockWordCountRegistry extends WordCountRegistry {
  override val getWordCounts: ZIO[Any, Nothing, List[WordCount]] =
    ZIO.succeed(List(WordCount(EventType("wow"), 3)))

  override def addWordCount(words: WordCount): ZIO[Any, Nothing, Unit] = ???
}

object WordCountRegistry {
  val mock: ZLayer[Any, Nothing, Has[WordCountRegistry]] =
    ZLayer.succeed(new MockWordCountRegistry)
}
