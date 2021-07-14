package io.janstenpickle.trace4cats.example

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import io.janstenpickle.trace4cats.sttp.client3.syntax._
import org.http4s.blaze.client.BlazeClientBuilder
import sttp.client3.http4s.Http4sBackend

import scala.concurrent.ExecutionContext

object SttpExample extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]
      client <- BlazeClientBuilder[IO](ExecutionContext.global).resource
      sttpBackend = Http4sBackend.usingClient(client, blocker)
      tracedBackend = sttpBackend.liftTrace()
    } yield tracedBackend).use { _ =>
      IO(ExitCode.Success)
    }
}
