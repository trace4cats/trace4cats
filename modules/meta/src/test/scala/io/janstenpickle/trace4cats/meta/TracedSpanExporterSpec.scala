package io.janstenpickle.trace4cats.meta

import cats.Eq
import cats.effect.{ContextShift, IO, Resource, Timer}
import fs2.{Chunk, Stream}
import fs2.concurrent.Queue
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.kernel.{BuildInfo, SpanExporter, SpanSampler}
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext

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

      assert(
        Eq.eqv(
          metaSpan.allAttributes,
          process.attributes ++ Map[String, AttributeValue](
            "exporter.name" -> exporterName,
            "trace4cats.version" -> BuildInfo.version,
            "batch.size" -> batch.spans.size,
            "service.name" -> process.serviceName
          ) ++ attributes
        )
      )
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
          Link(traceId, spanId)
        }
        .toList

      spans.size should be(batch.spans.size + 1)

      links match {
        case Nil => assert(true)
        case h :: t =>
          metaSpan.context.traceId should be(h.traceId)
          metaSpan.context.parent.map(_.spanId) should be(Some(h.spanId))

          metaSpan.links.fold(List.empty[Link])(_.toList) should contain theSameElementsAs t
      }
    }
  )

  def exporterTest(
    sampler: SpanSampler[IO],
    mapBatch: Batch[Chunk] => Batch[Chunk],
    test: (String, Map[String, AttributeValue], TraceProcess, Batch[Chunk], Chunk[CompletedSpan]) => Assertion,
    expectedMetaSpans: Long = 1
  ): Assertion =
    forAll {
      (exporterName: String, attributes: Map[String, AttributeValue], process: TraceProcess, spans: Batch[Chunk]) =>
        val batch = mapBatch(spans)

        (for {
          queue <- Resource.eval(Queue.circularBuffer[IO, CompletedSpan](batch.spans.size + 100))
          exporter = new SpanExporter[IO, Chunk] {
            override def exportBatch(batch: Batch[Chunk]): IO[Unit] =
              Stream.chunk(batch.spans).covary[IO].through(queue.enqueue).compile.drain
          }
          tracedExporter = TracedSpanExporter[IO](exporterName, attributes, process, sampler, exporter)
          _ <- Resource.eval(tracedExporter.exportBatch(batch))

          allSpans <- Resource.eval(queue.dequeue.take(batch.spans.size + expectedMetaSpans).compile.to(Chunk))
        } yield allSpans)
          .use { spans =>
            IO(test(exporterName, attributes, process, batch, spans))
          }
          .unsafeRunSync()

    }

}
