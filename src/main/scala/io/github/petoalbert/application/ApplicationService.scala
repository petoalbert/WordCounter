package io.github.petoalbert.application

import zio.{Has, URLayer, ZLayer}

trait ApplicationService {}

object ApplicationService {

  val live: URLayer[Any, Has[ApplicationService]] = ZLayer.succeed(
    new ApplicationService {}
  )

}
