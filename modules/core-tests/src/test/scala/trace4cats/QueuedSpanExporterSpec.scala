package trace4cats

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.testkit.TestInstances
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import fs2.Chunk
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import trace4cats.test.ArbitraryInstances._

class QueuedSpanExporterSpec extends AnyFlatSpec with Matchers with TestInstances with ScalaCheckDrivenPropertyChecks {
  behavior.of("QueuedSpanExporter")

  def refExporter(ref: Ref[IO, Chunk[CompletedSpan]]): SpanExporter[IO, Chunk] = new SpanExporter[IO, Chunk] {
    def exportBatch(batch: Batch[Chunk]): IO[Unit] = ref.update(_ ++ batch.spans)
  }

  implicit def logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  it should "export all spans on shutdown" in forAll { (span: CompletedSpan) =>
    val test = for {
      ref <- Ref.of[IO, Chunk[CompletedSpan]](Chunk.empty)
      _ <- QueuedSpanExporter[IO](50000, List("test" -> refExporter(ref))).use { exporter =>
        List.fill(10001)(exporter.exportBatch(Batch(Chunk.singleton(span)))).sequence
      }
      res <- ref.get
    } yield res

    val result = test.unsafeRunSync()
    result.size shouldBe 10001
  }

}
