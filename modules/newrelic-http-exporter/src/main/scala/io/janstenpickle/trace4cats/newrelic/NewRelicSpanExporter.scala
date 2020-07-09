package io.janstenpickle.trace4cats.newrelic

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import io.chrisdavenport.log4cats.Logger
import io.circe.Json
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.{Header, MediaType}

object NewRelicSpanExporter {

  def emberClient[F[_]: Concurrent: Timer: ContextShift: Logger](
    blocker: Blocker,
    apiKey: String,
    endpoint: Endpoint
  ): Resource[F, SpanExporter[F]] =
    EmberClientBuilder
      .default[F]
      .withLogger(Logger[F])
      .withBlocker(blocker)
      .build
      .evalMap(apply[F](_, apiKey, endpoint))

  def apply[F[_]: Sync: Timer](client: Client[F], apiKey: String, endpoint: Endpoint): F[SpanExporter[F]] =
    HttpSpanExporter[F, Json](
      client,
      endpoint.url,
      (batch: Batch) => Convert.toJson(batch),
      List(
        `Content-Type`(MediaType.application.json),
        Header("Api-Key", apiKey),
        Header("Data-Format", "newrelic"),
        Header("Data-Format-Version", "1")
      )
    )

}
