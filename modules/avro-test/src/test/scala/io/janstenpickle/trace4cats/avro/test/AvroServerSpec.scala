package io.janstenpickle.trace4cats.avro.test

import cats.data.NonEmptyList
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, IO, Resource}
import cats.syntax.all._
import cats.kernel.Eq
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.avro.{AvroSpanCompleter, AvroSpanExporter}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalacheck.Shrink
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

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  behavior.of("Avro TCP")

  it should "send batches" in {
    forAll { batch: Batch =>
      val ref = Ref.unsafe[IO, Option[Batch]](None)

      (for {
        server <- AvroServer.tcp[IO](
          blocker,
          _.evalMap { b =>
            ref.set(Some(b))
          }
        )
        _ <- server.compile.drain.background
        _ <- Resource.liftF(timer.sleep(2.seconds))
        completer <- AvroSpanExporter.tcp[IO](blocker)
      } yield completer).use(_.exportBatch(batch) >> timer.sleep(3.seconds)).unsafeRunSync()

      assert(Eq[Option[Batch]].eqv(ref.get.unsafeRunSync(), Some(batch)))
    }
  }

  it should "send individual spans in batches" in {
    forAll { (process: TraceProcess, spans: NonEmptyList[CompletedSpan]) =>
      val ref = Ref.unsafe[IO, Option[Batch]](None)

      (for {
        server <- AvroServer.tcp[IO](
          blocker,
          _.evalMap { b =>
            ref.set(Some(b))
          },
          port = 7778
        )
        _ <- server.compile.drain.background
        _ <- Resource.liftF(timer.sleep(2.seconds))
        completer <- AvroSpanCompleter.tcp[IO](blocker, process, port = 7778, batchTimeout = 1.second)
      } yield completer).use(c => spans.traverse(c.complete) >> timer.sleep(3.seconds)).unsafeRunSync()

      assert(Eq[Option[Batch]].eqv(ref.get.unsafeRunSync(), Some(Batch(process, spans.toList))))
    }
  }

  behavior.of("Avro UDP")

  it should "send batches" in {
    forAll { batch: Batch =>
      val ref = Ref.unsafe[IO, Option[Batch]](None)

      (for {
        server <- AvroServer.udp[IO](
          blocker,
          _.evalMap { b =>
            ref.set(Some(b))
          },
          port = 7779
        )
        _ <- server.compile.drain.background
        _ <- Resource.liftF(timer.sleep(2.seconds))
        completer <- AvroSpanExporter.udp[IO](blocker, port = 7779)
      } yield completer).use(_.exportBatch(batch) >> timer.sleep(3.seconds)).unsafeRunSync()

      assert(Eq[Option[Batch]].eqv(ref.get.unsafeRunSync(), Some(batch)))
    }
  }

  it should "send individual spans in batches" in {
    forAll { (process: TraceProcess, spans: NonEmptyList[CompletedSpan]) =>
      val ref = Ref.unsafe[IO, Option[Batch]](None)

      (for {
        server <- AvroServer.udp[IO](
          blocker,
          _.evalMap { b =>
            ref.set(Some(b))
          },
          port = 7780
        )
        _ <- server.compile.drain.background
        _ <- Resource.liftF(timer.sleep(2.seconds))
        completer <- AvroSpanCompleter.udp[IO](blocker, process, port = 7780, batchTimeout = 1.second)
      } yield completer).use(c => spans.traverse(c.complete) >> timer.sleep(3.seconds)).unsafeRunSync()

      assert(Eq[Option[Batch]].eqv(ref.get.unsafeRunSync(), Some(Batch(process, spans.toList))))
    }
  }
}
