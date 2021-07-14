package io.janstenpickle.trace4cats.example

import cats.effect.Blocker
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.inject.zio._
import io.janstenpickle.trace4cats.sttp.client3.syntax._
import org.http4s.blaze.client.BlazeClientBuilder
import sttp.client3.http4s.Http4sBackend
import zio._
import zio.interop.catz._

object SttpZioExample extends CatsApp {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    type F[x] = RIO[ZEnv, x]
    type G[x] = RIO[ZEnv with Has[Span[F]], x]
    implicit val spanProvide: Provide[F, G, Span[F]] = zioProvideSome

    ZIO
      .runtime[ZEnv]
      .zip(zio.blocking.blockingExecutor)
      .flatMap { case (rt, blockingExec) =>
        val ec = rt.platform.executor.asEC
        val blocker = Blocker.liftExecutionContext(blockingExec.asEC)
        (for {
          client <- BlazeClientBuilder[F](ec).resource
          sttpBackend = Http4sBackend.usingClient(client, blocker)
          tracedBackend = sttpBackend.liftTrace[G]()
        } yield tracedBackend)
          .use(_ => ZIO.never)
      }
      .exitCode
  }
}
