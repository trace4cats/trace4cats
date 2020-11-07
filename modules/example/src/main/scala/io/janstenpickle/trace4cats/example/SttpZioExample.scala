package io.janstenpickle.trace4cats.example

import cats.effect.Blocker
import io.janstenpickle.trace4cats.inject.zio._
import io.janstenpickle.trace4cats.sttp.backend.TracedBackend
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client.{NothingT, SttpBackend}
import sttp.client.http4s.Http4sBackend
import zio._
import zio.interop.catz._

object SttpZioExample extends CatsApp {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      blocker <- Blocker[Task]
      client <- BlazeClientBuilder[Task](blocker.blockingContext).resource
      sttpBackend = Http4sBackend.usingClient(client, blocker)
      tracedBackend: SttpBackend[ZIOTrace, Nothing, NothingT] =
        TracedBackend[Task, ZIOTrace, Nothing, NothingT](sttpBackend)
    } yield tracedBackend)
      .use { _ =>
        ZIO.never
      }
      .run
      .map {
        case Exit.Success(_) => ExitCode.success
        case Exit.Failure(_) => ExitCode.failure
      }
}
