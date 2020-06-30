package io.janstenpickle.trace4cats.opentelemetry

import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Timer}
import cats.syntax.functor._
import fs2.Stream
import fs2.concurrent.Queue
import io.grpc.ManagedChannelBuilder
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.resources.{Resource => OTResource}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object OpenTelemetrySpanCompleter {
  def apply[F[_]: Concurrent: ContextShift: Timer](
    blocker: Blocker,
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 55678,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] = {
    val processResource = Trace4CatsResource(process)

    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    val exporter = OtlpGrpcSpanExporter.newBuilder().setChannel(channel).build()

    def write(resource: OTResource, spans: List[CompletedSpan]) =
      blocker.delay(exporter.`export`(spans.map(Trace4CatsSpanData(resource, _)).asJavaCollection))

    for {
      queue <- Resource.liftF(Queue.circularBuffer[F, CompletedSpan](bufferSize))
      _ <- Stream
        .retry(
          queue.dequeue
            .groupWithin(batchSize, batchTimeout)
            .evalMap { spans =>
              write(processResource, spans.toList)
            }
            .compile
            .drain,
          5.seconds,
          _ + 1.second,
          Int.MaxValue
        )
        .compile
        .drain
        .background
    } yield
      new SpanCompleter[F] {

        override def complete(span: CompletedSpan): F[Unit] = queue.enqueue1(span)

        override def completeBatch(batch: Batch): F[Unit] =
          write(Trace4CatsResource(batch.process), batch.spans).void
      }
  }
}
