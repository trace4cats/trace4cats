package io.janstenpickle.trace4cats.example

import cats.data.Kleisli
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.sttp.backend.TracedBackend
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client.{NothingT, SttpBackend}
import sttp.client.http4s.Http4sBackend

object SttpExample extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]
      client <- BlazeClientBuilder[IO](blocker.blockingContext).resource
      sttpBackend = Http4sBackend.usingClient(client, blocker)
      tracedBackend: SttpBackend[Kleisli[IO, Span[IO], *], Nothing, NothingT] = TracedBackend[IO, Kleisli[
        IO,
        Span[IO],
        *
      ], Nothing, NothingT](sttpBackend)
    } yield tracedBackend).use { _ =>
      IO(ExitCode.Success)
    }
}
