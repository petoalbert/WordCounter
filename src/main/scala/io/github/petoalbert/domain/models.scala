package io.github.petoalbert.domain

case class EventType(value: String) extends AnyVal

case class WordCount(eventType: EventType, words: Int)

case class Event(eventType: EventType, words: Int, timestamp: Long)
