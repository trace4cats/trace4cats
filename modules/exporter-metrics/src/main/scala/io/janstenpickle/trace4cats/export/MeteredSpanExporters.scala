package io.janstenpickle.trace4cats.`export`

import cats.Show
import cats.data.NonEmptyList
import cats.effect.kernel.Sync
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import fs2.Chunk
import io.chrisdavenport.epimetheus.Histogram.UnlabelledHistogram
import io.chrisdavenport.epimetheus._
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import shapeless.Sized

object MeteredSpanExporters {
  def one[F[_]: Sync, A: Show](collectorRegistry: CollectorRegistry[F], maxBatchSize: Int, numBuckets: Int = 10)(
    label: A,
    exporter: SpanExporter[F, Chunk]
  ): F[SpanExporter[F, Chunk]] = apply(collectorRegistry, maxBatchSize, numBuckets)((label, exporter)).map(_.head._2)

  def apply[F[_]: Sync, A: Show](collectorRegistry: CollectorRegistry[F], maxBatchSize: Int, numBuckets: Int = 10)(
    exporter: (A, SpanExporter[F, Chunk]),
    others: (A, SpanExporter[F, Chunk])*
  ): F[NonEmptyList[(A, SpanExporter[F, Chunk])]] = {
    val buckets = 0.to(maxBatchSize, maxBatchSize / numBuckets).map(_.toDouble)

    def instrument(
      histogram: UnlabelledHistogram[F, A],
      errorCounter: UnlabelledCounter[F, A]
    ): (A, SpanExporter[F, Chunk]) => SpanExporter[F, Chunk] = (a, exporter) =>
      new SpanExporter[F, Chunk] {
        override def exportBatch(batch: Batch[Chunk]): F[Unit] =
          (exporter.exportBatch(batch) >> histogram.label(a).observe(batch.spans.size.toDouble))
            .onError { case _ =>
              errorCounter.label(a).inc
            }
      }

    for {
      batchHistogram <- Histogram
        .labelledBuckets(
          collectorRegistry,
          Name("trace4cats_exporter_batch_histogram"),
          "Number of span batch size histogram",
          Sized(Label("exporter_name")),
          { a: A => Sized(a.show) },
          buckets: _*
        )
      errorCounter <- Counter
        .labelled(
          collectorRegistry,
          Name("trace4cats_exporter_error_total"),
          "Number of span batches that could not be exported",
          Sized(Label("exporter_name")),
          { a: A => Sized(a.show) }
        )
    } yield {
      val instrumented = instrument(batchHistogram, errorCounter)

      NonEmptyList(exporter, others.toList).map { case (a, e) => a -> instrumented(a, e) }
    }
  }

}
