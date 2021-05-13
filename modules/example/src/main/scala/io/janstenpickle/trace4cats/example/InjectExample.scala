// Adapted from Natchez
// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.example

import cats.data.Kleisli
import cats.effect.kernel.{Async, Temporal}
import cats.effect.std.Random
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.instances.int._
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.syntax.partialOrder._
import cats.{Monad, Order, Parallel}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.`export`.CompleterConfig
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.TraceProcess
import io.janstenpickle.trace4cats.natchez.conversions.toNatchez._
import natchez.{Trace => NatchezTrace}

import scala.concurrent.duration._

/** Adapted from https://github.com/tpolecat/natchez/blob/b995b0ebf7b180666810f4edef46dce959596ace/modules/examples/src/main/scala/Example.scala
  *
  * This example demonstrates how to use Trace4Cats inject to implicitly pass spans around the callstack.
  */
object InjectExample extends IOApp {
  def entryPoint[F[_]: Async](process: TraceProcess): Resource[F, EntryPoint[F]] =
    AvroSpanCompleter.udp[F](process, config = CompleterConfig(batchTimeout = 50.millis)).map { completer =>
      EntryPoint[F](SpanSampler.probabilistic[F](0.05), completer)
    }

  // Intentionally slow parallel quicksort, to demonstrate branching. If we run too quickly it seems
  // to break Jaeger with "skipping clock skew adjustment" so let's pause a bit each time.
  def qsort[F[_]: Temporal: Parallel: Trace, A: Order](as: List[A]): F[List[A]] =
    Trace[F].span(as.mkString(",")) {
      Temporal[F].sleep(10.milli) *> {
        as match {
          case Nil => Monad[F].pure(Nil)
          case h :: t =>
            val (a, b) = t.partition(_ <= h)
            (qsort[F, A](a), qsort[F, A](b)).parMapN(_ ++ List(h) ++ _)
        }
      }
    }

  // Demonstrate implicit conversion from Trace4Cats trace to Natchez
  // use io.janstenpickle.trace4cats.natchez.conversions._ to do this
  def convertedTrace[F[_]: NatchezTrace]: F[Unit] = NatchezTrace[F].put("attribute" -> "test")

  def runF[F[_]: Temporal: Trace: Parallel: Random]: F[Unit] =
    Trace[F].span("Sort some stuff!") {
      for {
        as <- Random[F].nextIntBounded(1000).replicateA(100)
        _ <- qsort[F, Int](as)
        _ <- convertedTrace[F]
      } yield ()
    }

  override def run(args: List[String]): IO[ExitCode] =
    entryPoint[IO](TraceProcess("trace4cats"))
      .use { ep =>
        ep.root("this is the root span").use { span =>
          implicit val random: Random[Kleisli[IO, Span[IO], *]] = Random.javaUtilConcurrentThreadLocalRandom
          runF[Kleisli[IO, Span[IO], *]].run(span)
        }
      }
      .as(ExitCode.Success)
}
