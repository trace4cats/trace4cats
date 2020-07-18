package io.janstenpickle.trace4cats.example

import cats.data.Kleisli
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource, Sync}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.example.Fs2Example.entryPoint
import io.janstenpickle.trace4cats.http4s.client.syntax._
import io.janstenpickle.trace4cats.http4s.server.syntax._
import io.janstenpickle.trace4cats.model.TraceProcess
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

object Http4sExample extends IOApp {

  def makeRoutes[F[_]: Sync](client: Client[Kleisli[F, Span[F], *]]): HttpRoutes[Kleisli[F, Span[F], *]] = {
    object dsl extends Http4sDsl[Kleisli[F, Span[F], *]]
    import dsl._

    HttpRoutes.of {
      case req @ GET -> Root / "forward" => client.expect[String](req).map(Ok(_))
    }
  }

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]
      implicit0(logger: Logger[IO]) <- Resource.liftF(Slf4jLogger.create[IO])
      ep <- entryPoint[IO](blocker, TraceProcess("trace4catsFS2"))

      client <- EmberClientBuilder.default[IO].withBlocker(blocker).withLogger(logger).build

      routes = makeRoutes(client.inject(ep)) // use implicit syntax to inject an entry point to http client

      server <- EmberServerBuilder
        .default[IO]
        .withBlocker(blocker)
        .withLogger(logger)
        .withHost("0.0.0.0")
        .withPort(8080)
        .withHttpApp(routes.inject(ep).orNotFound) // use implicit syntax to inject an entry point to http routes
        .build
    } yield server).use { _ =>
      IO(ExitCode.Success)
    }
}
