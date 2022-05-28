package trace4cats.meta

import cats.Eq
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Chunk, Stream}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import trace4cats.kernel.{BuildInfo, SpanSampler}
import trace4cats.model._
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import trace4cats.test.ArbitraryInstances

class PipeTracerSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  it should "trace every span batch when when all meta traces are sampled" in exporterTest(
    SpanSampler.always[IO],
    spans => Batch(spans.spans.map(_.copy(metaTrace = None))),
    (attributes, process, batch, spans) => {
      lazy val metaSpan = spans.find(_.name == "trace4cats.receive.batch").get

      spans.size should be(batch.spans.size + 1)

      assert(
        Eq.eqv(
          metaSpan.allAttributes,
          process.attributes ++ Map[String, AttributeValue](
            "trace4cats.version" -> BuildInfo.version,
            "batch.size" -> batch.spans.size,
            "service.name" -> process.serviceName
          ) ++ attributes
        )
      )
      metaSpan.kind should be(SpanKind.Consumer)
    }
  )

  it should "trace no span batch when when all meta traces are dropped" in exporterTest(
    SpanSampler.never[IO],
    spans => Batch(spans.spans.map(_.copy(metaTrace = None))),
    (_, _, batch, spans) => {
      spans.size should be(batch.spans.size)
      assert(spans.forall(_.metaTrace.isEmpty))
    },
    0
  )

  it should "add parent links to the trace when exported spans have meta-traces" in exporterTest(
    SpanSampler.always[IO],
    identity,
    (_, _, batch, spans) => {
      lazy val metaSpan = spans.find(_.name == "trace4cats.receive.batch").get

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
    test: (Map[String, AttributeValue], TraceProcess, Batch[Chunk], Chunk[CompletedSpan]) => Assertion,
    expectedMetaSpans: Long = 1
  ): Assertion =
    forAll { (attributes: Map[String, AttributeValue], process: TraceProcess, spans: Batch[Chunk]) =>
      val batch = mapBatch(spans)

      val pipeTracer = PipeTracer[IO](attributes, process, sampler)

      (for {
        s <- Stream
          .chunk(batch.spans)
          .covary[IO]
          .through(pipeTracer)
          .take(batch.spans.size + expectedMetaSpans)
          .compile
          .to(Chunk)
      } yield test(attributes, process, batch, s))
        .unsafeRunSync()

    }

}
