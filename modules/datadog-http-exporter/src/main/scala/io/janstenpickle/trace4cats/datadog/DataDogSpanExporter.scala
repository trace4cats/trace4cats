package io.janstenpickle.trace4cats.datadog

import cats.Foldable
import cats.effect.kernel.{Async, Resource, Temporal}
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.Method.PUT
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

object DataDogSpanExporter {
  def blazeClient[F[_]: Async, G[_]: Foldable](
    ec: ExecutionContext, //TODO: keep parameter or replace with EC.global as recommended?
    host: String = "localhost",
    port: Int = 8126
  ): Resource[F, SpanExporter[F, G]] =
    BlazeClientBuilder[F](ec).resource
      .evalMap(apply[F, G](_, host, port))

  def apply[F[_]: Temporal, G[_]: Foldable](
    client: Client[F],
    host: String = "localhost",
    port: Int = 8126
  ): F[SpanExporter[F, G]] =
    HttpSpanExporter[F, G, List[List[DataDogSpan]]](
      client,
      s"http://$host:$port/v0.3/traces",
      (batch: Batch[G]) => DataDogSpan.fromBatch(batch),
      PUT
    )
}
