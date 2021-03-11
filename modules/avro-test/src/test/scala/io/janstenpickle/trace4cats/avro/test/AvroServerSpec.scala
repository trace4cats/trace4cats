package io.janstenpickle.trace4cats.avro.test

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.kernel.Eq
import cats.syntax.all._
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.CompleterConfig
import io.janstenpickle.trace4cats.avro.{AvroSpanCompleter, AvroSpanExporter}
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalacheck.Shrink
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration._

class AvroServerSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 1, maxDiscardedFactor = 50.0)

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  behavior.of("Avro TCP")

  it should "send batches" in {
    forAll { batch: Batch[List] =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.tcp[IO](_.evalMap(queue.offer))
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanExporter.tcp[IO, List]()
          } yield completer).use(_.exportBatch(batch) >> IO.sleep(3.seconds)) >> Stream
            .fromQueueUnterminated(queue)
            .take(batch.spans.size.toLong)
            .compile
            .toList
            .map { spans =>
              Eq.eqv(spans, batch.spans)
            }
        }
        .unsafeRunSync()
    }
  }

  it should "send individual spans in batches" in {
    forAll { (process: TraceProcess, spans: NonEmptyList[CompletedSpan.Builder]) =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.tcp[IO](_.evalMap(queue.offer), port = 7778)
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanCompleter.tcp[IO](
              process,
              port = 7778,
              config = CompleterConfig(batchTimeout = 1.second)
            )
          } yield completer).use(c => spans.traverse(c.complete) >> IO.sleep(3.seconds)) >> Stream
            .fromQueueUnterminated(queue)
            .take(spans.size.toLong)
            .compile
            .toList
            .map { s =>
              assert(Eq.eqv(s, spans.toList.map(_.build(process))))
            }
        }
        .unsafeRunSync()

    }
  }

  behavior.of("Avro UDP")

  it should "send batches" in {
    forAll { batch: Batch[List] =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.udp[IO](_.evalMap(queue.offer), port = 7779)
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanExporter.udp[IO, List](port = 7779)
          } yield completer).use(_.exportBatch(batch) >> IO.sleep(3.seconds)) >> Stream
            .fromQueueUnterminated(queue)
            .take(batch.spans.size.toLong)
            .compile
            .toList
            .map { spans =>
              Eq.eqv(spans, batch.spans)
            }
        }
        .unsafeRunSync()
    }
  }

  it should "send individual spans in batches" in {
    forAll { (process: TraceProcess, spans: NonEmptyList[CompletedSpan.Builder]) =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.udp[IO](_.evalMap(queue.offer), port = 7780)
            _ <- server.compile.drain.background
            _ <- Resource.eval(IO.sleep(2.seconds))
            completer <- AvroSpanCompleter.udp[IO](
              process,
              port = 7780,
              config = CompleterConfig(batchTimeout = 1.second)
            )
          } yield completer).use(c => spans.traverse(c.complete) >> IO.sleep(3.seconds)) >> Stream
            .fromQueueUnterminated(queue)
            .take(spans.size.toLong)
            .compile
            .toList
            .map { s =>
              assert(Eq.eqv(s, spans.toList.map(_.build(process))))
            }
        }
        .unsafeRunSync()
    }
  }
}
