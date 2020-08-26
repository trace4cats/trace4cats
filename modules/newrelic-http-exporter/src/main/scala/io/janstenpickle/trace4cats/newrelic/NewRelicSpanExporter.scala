package io.janstenpickle.trace4cats.newrelic

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Sync, Timer}
import io.circe.Json
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.{Header, MediaType}

object NewRelicSpanExporter {

  def emberClient[F[_]: ConcurrentEffect: Timer: ContextShift](
    blocker: Blocker,
    apiKey: String,
    endpoint: Endpoint
  ): Resource[F, SpanExporter[F]] =
    BlazeClientBuilder[F](blocker.blockingContext).resource.evalMap(apply[F](_, apiKey, endpoint))

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
