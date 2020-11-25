package io.janstenpickle.trace4cats.example

import cats.effect.{Blocker, Resource}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.base.context.zio._
import io.janstenpickle.trace4cats.example.Fs2Example.entryPoint
import io.janstenpickle.trace4cats.http4s.client.syntax._
import io.janstenpickle.trace4cats.http4s.common.Http4sRequestFilter
import io.janstenpickle.trace4cats.http4s.server.syntax._
import io.janstenpickle.trace4cats.inject.zio._
import io.janstenpickle.trace4cats.model.TraceProcess
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import zio._
import zio.interop.catz._

object Http4sZioExample extends CatsApp {

  def makeRoutes(client: Client[ZIOTrace]): HttpRoutes[ZIOTrace] = {
    object dsl extends Http4sDsl[ZIOTrace]
    import dsl._

    HttpRoutes.of { case req @ GET -> Root / "forward" =>
      client.expect[String](req).flatMap(Ok(_))
    }
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      blocker <- Blocker[Task]
      implicit0(logger: Logger[Task]) <- Resource.liftF(Slf4jLogger.create[Task])
      ep <- entryPoint[Task](blocker, TraceProcess("trace4catsHttp4s"))

      client <- BlazeClientBuilder[Task](blocker.blockingContext).resource

      routes = makeRoutes(client.liftTrace()) // use implicit syntax to lift http client to the trace context

      server <-
        BlazeServerBuilder[Task](blocker.blockingContext)
          .bindHttp(8080, "0.0.0.0")
          .withHttpApp(
            routes.inject(ep, requestFilter = Http4sRequestFilter.kubernetesPrometheus).orNotFound
          ) // use implicit syntax to inject an entry point to http routes
          .resource
    } yield server)
      .use { _ =>
        ZIO.never
      }
      .run
      .map {
        case Exit.Success(_) => ExitCode.success
        case Exit.Failure(_) => ExitCode.failure
      }
}
