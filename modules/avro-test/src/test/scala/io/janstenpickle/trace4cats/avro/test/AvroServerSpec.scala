package io.janstenpickle.trace4cats.avro.test

import cats.data.NonEmptyList
import cats.effect.{Blocker, IO, Resource}
import cats.kernel.Eq
import cats.syntax.all._
import fs2.Chunk
import fs2.concurrent.Queue
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.CompleterConfig
import io.janstenpickle.trace4cats.avro.{AvroSpanCompleter, AvroSpanExporter}
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalacheck.{Arbitrary, Gen, Shrink}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class AvroServerSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  implicit val contextShift = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)

  val blocker = Blocker.liftExecutionContext(ExecutionContext.global)

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 1, maxDiscardedFactor = 50.0)

  implicit val nBatchesArbitrary: Arbitrary[(Int, List[Batch[Chunk]])] = Arbitrary(for {
    n <- Gen.choose(2, 4)
    list <- Gen.listOfN(n, batchArb.arbitrary)
  } yield (n, list))

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  behavior.of("Avro TCP")

  it should "send batches" in {
    forAll { batch: Batch[Chunk] =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.tcp[IO](blocker, queue.enqueue)
            _ <- server.compile.drain.background
            _ <- Resource.liftF(timer.sleep(2.seconds))
            completer <- AvroSpanExporter.tcp[IO, Chunk](blocker)
          } yield completer).use(_.exportBatch(batch) >> timer.sleep(3.seconds)) >> queue.dequeue
            .take(batch.spans.size.toLong)
            .compile
            .to(Chunk)
            .map { spans =>
              Eq.eqv(spans, batch.spans)
            }
        }
        .unsafeRunSync()
    }
  }

  it should "send batches via multiple fibers" in {
    forAll { nBatches: (Int, List[Batch[Chunk]]) =>
      val (n, batches) = nBatches
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.tcp[IO](blocker, queue.enqueue)
            _ <- server.compile.drain.background
            _ <- Resource.liftF(timer.sleep(2.seconds))
            completer <- AvroSpanExporter.tcp[IO, Chunk](blocker, numFibers = n)
          } yield completer).use(c => batches.traverse_(c.exportBatch) >> timer.sleep(3.seconds)) >> queue.dequeue
            .take(batches.foldMap(_.spans.size.toLong))
            .compile
            .toList
            .map { spans =>
              Eq.eqv(spans.sortBy(_.context.spanId.show), batches.flatMap(_.spans.toList).sortBy(_.context.spanId.show))
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
            server <- AvroServer.tcp[IO](blocker, queue.enqueue, port = 7778)
            _ <- server.compile.drain.background
            _ <- Resource.liftF(timer.sleep(2.seconds))
            completer <- AvroSpanCompleter.tcp[IO](
              blocker,
              process,
              port = 7778,
              config = CompleterConfig(batchTimeout = 1.second)
            )
          } yield completer).use(c => spans.traverse(c.complete) >> timer.sleep(3.seconds)) >> queue.dequeue
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
    forAll { batch: Batch[Chunk] =>
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.udp[IO](blocker, queue.enqueue, port = 7779)
            _ <- server.compile.drain.background
            _ <- Resource.liftF(timer.sleep(2.seconds))
            completer <- AvroSpanExporter.udp[IO, Chunk](blocker, port = 7779)
          } yield completer).use(_.exportBatch(batch) >> timer.sleep(3.seconds)) >> queue.dequeue
            .take(batch.spans.size.toLong)
            .compile
            .to(Chunk)
            .map { spans =>
              Eq.eqv(spans, batch.spans)
            }
        }
        .unsafeRunSync()
    }
  }

  it should "send batches via multiple fibers" in {
    forAll { nBatches: (Int, List[Batch[Chunk]]) =>
      val (n, batches) = nBatches
      Queue
        .unbounded[IO, CompletedSpan]
        .flatMap { queue =>
          (for {
            server <- AvroServer.udp[IO](blocker, queue.enqueue, port = 7781)
            _ <- server.compile.drain.background
            _ <- Resource.liftF(timer.sleep(2.seconds))
            completer <- AvroSpanExporter.udp[IO, Chunk](blocker, port = 7781, numFibers = n)
          } yield completer).use(c => batches.traverse_(c.exportBatch) >> timer.sleep(3.seconds)) >> queue.dequeue
            .take(batches.foldMap(_.spans.size.toLong))
            .compile
            .toList
            .map { spans =>
              Eq.eqv(spans.sortBy(_.context.spanId.show), batches.flatMap(_.spans.toList).sortBy(_.context.spanId.show))
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
            server <- AvroServer.udp[IO](blocker, queue.enqueue, port = 7780)
            _ <- server.compile.drain.background
            _ <- Resource.liftF(timer.sleep(2.seconds))
            completer <- AvroSpanCompleter.udp[IO](
              blocker,
              process,
              port = 7780,
              config = CompleterConfig(batchTimeout = 1.second)
            )
          } yield completer).use(c => spans.traverse(c.complete) >> timer.sleep(3.seconds)) >> queue.dequeue
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
