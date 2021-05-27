package io.janstenpickle.trace4cats.zipkin

import cats.Foldable
import cats.effect.kernel.{Async, Resource}
import cats.syntax.applicative._
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import org.http4s.client.Client
import org.http4s.blaze.client.BlazeClientBuilder

import scala.concurrent.ExecutionContext

object ZipkinHttpSpanExporter {

  def blazeClient[F[_]: Async, G[_]: Foldable](
    host: String = "localhost",
    port: Int = 9411,
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] = for {
    ec <- Resource.eval(ec.fold(Async[F].executionContext)(_.pure))
    client <- BlazeClientBuilder[F](ec).resource
    exporter <- Resource.eval(apply[F, G](client, host, port))
  } yield exporter

  def apply[F[_]: Async, G[_]: Foldable](
    client: Client[F],
    host: String = "localhost",
    port: Int = 9411
  ): F[SpanExporter[F, G]] =
    HttpSpanExporter[F, G, String](client, s"http://$host:$port/api/v2/spans", ZipkinSpan.toJsonString[G](_))

  def apply[F[_]: Async, G[_]: Foldable](client: Client[F], uri: String): F[SpanExporter[F, G]] =
    HttpSpanExporter[F, G, String](client, uri, ZipkinSpan.toJsonString[G](_))
}
