package io.janstenpickle.trace4cats.datadog

import cats.Foldable
import cats.effect.kernel.{Async, Resource, Temporal}
import cats.syntax.applicative._
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.Method.PUT
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.blaze.client.BlazeClientBuilder

import scala.concurrent.ExecutionContext

object DataDogSpanExporter {
  def blazeClient[F[_]: Async, G[_]: Foldable](
    host: String = "localhost",
    port: Int = 8126,
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] = for {
    ec <- Resource.eval(ec.fold(Async[F].executionContext)(_.pure))
    client <- BlazeClientBuilder[F](ec).resource
    exporter <- Resource.eval(apply[F, G](client, host, port))
  } yield exporter

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
