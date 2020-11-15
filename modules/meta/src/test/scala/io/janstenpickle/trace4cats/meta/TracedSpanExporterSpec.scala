package io.janstenpickle.trace4cats.meta

import cats.effect.{ContextShift, IO, Resource, Timer}
import fs2.Chunk
import fs2.concurrent.Queue
import io.janstenpickle.trace4cats.kernel.{BuildInfo, SpanExporter, SpanSampler}
import io.janstenpickle.trace4cats.model.{AttributeValue, Batch, CompletedSpan, Link, MetaTrace, SpanKind, TraceProcess}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.scalatest.Assertion

import scala.concurrent.duration._

class TracedSpanExporterSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ArbitraryInstances {

  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  it should "trace every span batch when when all meta traces are sampled" in exporterTest(
    SpanSampler.always[IO],
    spans => Batch(spans.spans.map(_.copy(metaTrace = None))),
    (exporterName, attributes, process, batch, spans) => {
      lazy val metaSpan = spans.find(_.name == "trace4cats.export.batch").get

      spans.size should be(batch.spans.size + 1)

      metaSpan.allAttributes should contain theSameElementsAs (process.attributes ++ Map[String, AttributeValue](
        "exporter.name" -> exporterName,
        "trace4cats.version" -> BuildInfo.version,
        "batch.size" -> batch.spans.size,
        "service.name" -> process.serviceName
      ) ++ attributes)
      metaSpan.kind should be(SpanKind.Producer)
    }
  )

  it should "trace no span batch when when all meta traces are dropped" in exporterTest(
    SpanSampler.never[IO],
    spans => Batch(spans.spans.map(_.copy(metaTrace = None))),
    (_, _, _, batch, spans) => {
      spans.size should be(batch.spans.size)
      assert(spans.forall(_.metaTrace.isEmpty))
    },
    0
  )

  it should "add parent links to the trace when exported spans have meta-traces" in exporterTest(
    SpanSampler.always[IO],
    identity,
    (_, _, _, batch, spans) => {
      lazy val metaSpan = spans.find(_.name == "trace4cats.export.batch").get

      lazy val links = batch.spans
        .collect[Link] { case CompletedSpan(_, _, _, _, _, _, _, _, _, Some(MetaTrace(traceId, spanId))) =>
          Link.Parent(traceId, spanId)
        }
        .toList

      spans.size should be(batch.spans.size + 1)

      metaSpan.links.fold(List.empty[Link])(_.toList) should contain theSameElementsAs links
    }
  )

  def exporterTest(
    sampler: SpanSampler[IO],
    mapBatch: Batch[Chunk] => Batch[Chunk],
    test: (String, List[(String, AttributeValue)], TraceProcess, Batch[Chunk], Chunk[CompletedSpan]) => Assertion,
    expectedMetaSpans: Long = 1
  ): Assertion =
    forAll {
      (exporterName: String, attributes: List[(String, AttributeValue)], process: TraceProcess, spans: Batch[Chunk]) =>
        val batch = mapBatch(spans)

        (for {
          queue <- Resource.liftF(Queue.circularBuffer[IO, CompletedSpan](batch.spans.size + 100))
          exporter = new SpanExporter[IO, Chunk] {
            override def exportBatch(batch: Batch[Chunk]): IO[Unit] =
              Stream.chunk(batch.spans).covary[IO].through(queue.enqueue).compile.drain
          }
          tracedExporter <- TracedSpanExporter[IO](
            exporterName,
            attributes,
            process,
            sampler,
            exporter,
            batchTimeout = 1.milli
          )
          _ <- Resource.liftF(tracedExporter.exportBatch(batch))

          allSpans <- Resource.liftF(queue.dequeue.take(batch.spans.size + expectedMetaSpans).compile.to(Chunk))
        } yield allSpans)
          .use { spans =>
            IO(test(exporterName, attributes, process, batch, spans))
          }
          .unsafeRunSync()

    }

}
