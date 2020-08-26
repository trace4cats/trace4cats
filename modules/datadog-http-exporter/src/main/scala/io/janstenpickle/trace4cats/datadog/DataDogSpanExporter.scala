package io.janstenpickle.trace4cats.datadog

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Sync, Timer}
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.Method.PUT
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

object DataDogSpanExporter {
  def emberClient[F[_]: ConcurrentEffect: Timer: ContextShift: Logger](
    blocker: Blocker,
    host: String = "localhost",
    port: Int = 8126
  ): Resource[F, SpanExporter[F]] =
    BlazeClientBuilder[F](blocker.blockingContext).resource
      .evalMap(apply[F](_, host, port))

  def apply[F[_]: Sync: Timer](client: Client[F], host: String = "localhost", port: Int = 8126): F[SpanExporter[F]] =
    HttpSpanExporter[F, List[List[DataDogSpan]]](
      client,
      s"http://$host:$port/v0.3/traces",
      (batch: Batch) => DataDogSpan.fromBatch(batch),
      PUT
    )
}
