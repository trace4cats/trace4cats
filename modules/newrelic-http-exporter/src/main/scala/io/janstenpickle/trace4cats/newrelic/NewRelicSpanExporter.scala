package io.janstenpickle.trace4cats.newrelic

import cats.Foldable
import cats.effect.kernel.{Async, Resource, Temporal}
import cats.syntax.applicative._
import io.circe.Json
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.{Header, MediaType}

import scala.concurrent.ExecutionContext

object NewRelicSpanExporter {
  def blazeClient[F[_]: Async, G[_]: Foldable](
    apiKey: String,
    endpoint: Endpoint,
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] = for {
    ec <- Resource.eval(ec.fold(Async[F].executionContext)(_.pure))
    client <- BlazeClientBuilder[F](ec).resource
    exporter <- Resource.eval(apply[F, G](client, apiKey, endpoint))
  } yield exporter

  def apply[F[_]: Temporal, G[_]: Foldable](
    client: Client[F],
    apiKey: String,
    endpoint: Endpoint
  ): F[SpanExporter[F, G]] =
    HttpSpanExporter[F, G, Json](
      client,
      endpoint.url,
      (batch: Batch[G]) => Convert.toJson(batch),
      List[Header.ToRaw](
        `Content-Type`(MediaType.application.json),
        "Api-Key" -> apiKey,
        "Data-Format" -> "newrelic",
        "Data-Format-Version" -> "1"
      )
    )
}
