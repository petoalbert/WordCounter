package io.github.petoalbert.api

import de.heikoseeberger.akkahttpziojson.ZioJsonSupport
import io.github.petoalbert.domain.{EventType, WordCount}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

trait JsonSupport extends ZioJsonSupport {
  implicit val eventTypeEncoder: JsonEncoder[EventType] = JsonEncoder.string.contramap(_.value)
  implicit val eventTypeDecoder: JsonDecoder[EventType] = JsonDecoder.string.map(EventType)

  implicit val wordCountsEncoder: JsonEncoder[WordCount] = DeriveJsonEncoder.gen[WordCount]
  implicit val wordCountDecoder: JsonDecoder[WordCount]  = DeriveJsonDecoder.gen[WordCount]
}

object JsonSupport extends JsonSupport
