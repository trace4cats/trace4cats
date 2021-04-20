package io.janstenpickle.trace4cats.example

import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.inject.zio._
import io.janstenpickle.trace4cats.sttp.client.syntax._
import org.http4s.EntityBody
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client.SttpBackend
import sttp.client.http4s.Http4sBackend
import zio._
import zio.interop.catz._

import scala.concurrent.ExecutionContext
import cats.effect.Resource

object SttpZioExample extends CatsApp {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      blocker <- Resource.unit[Task]
      client <- BlazeClientBuilder[Task](ExecutionContext.global).resource
      sttpBackend = Http4sBackend.usingClient(client, blocker): SttpBackend[Task, EntityBody[Task], INothingT]
      tracedBackend = sttpBackend.liftTrace[RIO[Span[Task], *]]()
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
