package io.janstenpickle.trace4cats.sampling.dynamic.http4s

import cats.effect.kernel.{Resource, Temporal}
import cats.syntax.flatMap._
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.sampling.dynamic.config.{ConfiguredHotSwapSpanSampler, SamplerConfig}
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl

object SamplerHttpRoutes {
  implicit val circeConfig: Configuration = Configuration.default.withDefaults.withDiscriminator("samplerType")
  implicit val configCodec: Codec[SamplerConfig] = deriveConfiguredCodec

  def create[F[_]: Temporal](
    initialConfig: SamplerConfig = SamplerConfig.Never
  ): Resource[F, (SpanSampler[F], HttpRoutes[F])] =
    ConfiguredHotSwapSpanSampler[F](initialConfig).map { sampler =>
      object dsl extends Http4sDsl[F]
      import dsl._

      (
        sampler,
        HttpRoutes.of[F] {
          case GET -> Root / "config" => Ok(sampler.getConfig)
          case req @ POST -> Root / "config" => Accepted(req.as[SamplerConfig].flatMap(sampler.updateConfig))
          case POST -> Root / "killswitch" => Ok(sampler.updateConfig(SamplerConfig.Never))
        }
      )
    }
}
