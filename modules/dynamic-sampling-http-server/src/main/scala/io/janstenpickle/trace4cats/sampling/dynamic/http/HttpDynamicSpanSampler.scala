package io.janstenpickle.trace4cats.sampling.dynamic.http

import cats.Applicative
import cats.effect.kernel.{Async, Resource}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.sampling.dynamic.SamplerConfig
import io.janstenpickle.trace4cats.sampling.dynamic.http4s.SamplerHttpRoutes
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

object HttpDynamicSpanSampler {
  def build[F[_]: Async](
    builder: BlazeServerBuilder[F] => BlazeServerBuilder[F],
    endpoint: String = "trace4cats",
    initialConfig: SamplerConfig = SamplerConfig.Never,
    executionContext: Option[ExecutionContext] = None
  ): Resource[F, SpanSampler[F]] = for {
    (sampler, routes) <- SamplerHttpRoutes.create[F](initialConfig)
    ec <- Resource.eval(executionContext.fold(Async[F].executionContext)(Applicative[F].pure))
    _ <- builder(BlazeServerBuilder[F](ec).bindHttp(port = 8080, host = "0.0.0.0"))
      .withHttpApp(Router(endpoint -> routes).orNotFound)
      .resource
  } yield sampler

  def create[F[_]: Async](
    bindHost: String = "0.0.0.0",
    bindPort: Int = 8080,
    endpoint: String = "trace4cats",
    initialConfig: SamplerConfig = SamplerConfig.Never,
    executionContext: Option[ExecutionContext] = None
  ): Resource[F, SpanSampler[F]] =
    build[F](
      (builder: BlazeServerBuilder[F]) => builder.bindHttp(port = bindPort, host = bindHost),
      endpoint,
      initialConfig,
      executionContext
    )

}
