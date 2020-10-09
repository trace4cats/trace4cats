package io.janstenpickle.trace4cats.http4s

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO}

import scala.concurrent.ExecutionContext

package object client {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))
}
