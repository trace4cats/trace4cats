package trace4cats

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.std.Random
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

import scala.concurrent.duration._

class QueuedSpanCompleterSpec extends AnyFlatSpec with Matchers with TestInstances with ScalaCheckDrivenPropertyChecks {
  behavior.of("QueuedSpanCompleter")

  def delayedExporter(ref: Ref[IO, Int]): SpanExporter[IO, Chunk] = new SpanExporter[IO, Chunk] {
    def exportBatch(batch: Batch[Chunk]): IO[Unit] = IO.sleep(10.millis) >> ref.update(_ + batch.spans.size)
  }

  def refExporter(ref: Ref[IO, Chunk[CompletedSpan]]): SpanExporter[IO, Chunk] = new SpanExporter[IO, Chunk] {
    def exportBatch(batch: Batch[Chunk]): IO[Unit] = ref.update(_ ++ batch.spans)
  }

  implicit def logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val rps = 1000

  it should "not block on complete" in forAll { (builder: CompletedSpan.Builder) =>
    val test = for {
      ref <- Ref.of[IO, Int](0)
      random: Random[IO] <- Random.scalaUtilRandom[IO]
      exporter = delayedExporter(ref)
      res <- QueuedSpanCompleter[IO](
        TraceProcess("completer-test"),
        exporter,
        CompleterConfig(bufferSize = 5, batchSize = 1)
      ).use { completer =>
        val randomRequestTime = for {
          rand <- random.betweenDouble(0, 1)
          _ <- IO.sleep((1.second * rand).asInstanceOf[FiniteDuration])
          res <- completer.complete(builder).timed
        } yield res._1
        List.fill(rps)(randomRequestTime).parSequence.map(_.max)
      }
    } yield res

    val result = test.unsafeRunSync()
    result shouldBe (<(10.millis))
  }

  it should "export all spans on shutdown" in forAll { (builder: CompletedSpan.Builder) =>
    val test = for {
      ref <- Ref.of[IO, Chunk[CompletedSpan]](Chunk.empty)
      exporter = refExporter(ref)
      _ <- QueuedSpanCompleter[IO](
        TraceProcess("completer-test"),
        exporter,
        CompleterConfig(bufferSize = 50000, batchSize = 200)
      ).use { completer =>
        List.fill(10000)(completer.complete(builder)).sequence
      }
      res <- ref.get
    } yield res

    val result = test.unsafeRunSync()
    result.size shouldBe 10000
  }
}
