package io.janstenpickle.trace4cats.datadog

import cats.Foldable
import cats.effect.{ConcurrentEffect, Resource, Sync, Timer}
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.Method.PUT
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.blaze.client.BlazeClientBuilder

import scala.concurrent.ExecutionContext

object DataDogSpanExporter {
  def blazeClient[F[_]: ConcurrentEffect: Timer, G[_]: Foldable](
    host: String = "localhost",
    port: Int = 8126,
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] =
    // TODO: CE3 - use Async[F].executionContext
    BlazeClientBuilder[F](ec.getOrElse(ExecutionContext.global)).resource
      .evalMap(apply[F, G](_, host, port))

  def apply[F[_]: Sync: Timer, G[_]: Foldable](
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
