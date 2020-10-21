package io.janstenpickle.trace4cats.sttp.backend

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO}

import scala.concurrent.ExecutionContext

object Instances {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))

}
