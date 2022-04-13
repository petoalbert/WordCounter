package io.github.petoalbert.config

import zio.config.magnolia.DeriveConfigDescriptor
import akka.http.interop.HttpServer
import io.github.petoalbert.application.{JsonProcessor, WordCountRegistry}

final case class AppConfig(
  api: HttpServer.Config,
  wordcount: WordCountRegistry.Config,
  jsonProcessor: JsonProcessor.Config
)

object AppConfig {
  val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]
}
