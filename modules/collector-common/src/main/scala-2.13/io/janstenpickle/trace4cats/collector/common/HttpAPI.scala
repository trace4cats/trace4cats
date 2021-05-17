package io.janstenpickle.trace4cats.collector.common

import cats.Monad
import cats.effect.ExitCode
import cats.effect.kernel.{Async, Sync}
import cats.syntax.functor._
import cats.syntax.semigroupk._
import io.chrisdavenport.epimetheus.CollectorRegistry
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.metrics.prometheus.PrometheusExportService
import org.http4s.server.blaze.BlazeServerBuilder

object HttpAPI {
  def housekeepingRoutes[F[_]: Monad]: HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._

    HttpRoutes.of {
      case GET -> Root / "liveness" => Ok("ok")
      case GET -> Root / "healthcheck" => Ok("ok")
    }
  }

  def allRoutes[F[_]: Sync](cr: CollectorRegistry[F]): HttpRoutes[F] =
    PrometheusExportService.service(CollectorRegistry.Unsafe.asJava(cr)) <+> housekeepingRoutes

  def server[F[_]: Async](port: Int, cr: CollectorRegistry[F]): F[fs2.Stream[F, ExitCode]] =
    Async[F].executionContext.map(
      BlazeServerBuilder(_).bindHttp(port = port).withHttpApp(allRoutes[F](cr).orNotFound).serve
    )

}
