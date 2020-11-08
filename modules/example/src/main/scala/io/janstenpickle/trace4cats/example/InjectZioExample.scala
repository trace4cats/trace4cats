// Adapted from Natchez
// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.example

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.instances.int._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.syntax.partialOrder._
import cats.{Monad, Order, Parallel}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.inject.zio._
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.TraceProcess
import io.janstenpickle.trace4cats.natchez.conversions._
import natchez.{Trace => NatchezTrace}
import zio.interop.catz._
import zio._

import scala.concurrent.duration._
import scala.util.Random

/** Adapted from https://github.com/tpolecat/natchez/blob/b995b0ebf7b180666810f4edef46dce959596ace/modules/examples/src/main/scala/Example.scala
  *
  * This example demonstrates how to use Trace4Cats inject to implicitly pass spans around the callstack.
  */
object InjectZioExample extends CatsApp {
  def entryPoint[F[_]: Concurrent: ContextShift: Timer: Parallel: Logger](
    blocker: Blocker,
    process: TraceProcess
  ): Resource[F, EntryPoint[F]] =
    AvroSpanCompleter.udp[F](blocker, process, batchTimeout = 50.millis).map { completer =>
      EntryPoint[F](SpanSampler.probabilistic[F](0.05), completer)
    }

  // Intentionally slow parallel quicksort, to demonstrate branching. If we run too quickly it seems
  // to break Jaeger with "skipping clock skew adjustment" so let's pause a bit each time.
  def qsort[F[_]: Monad: Parallel: Trace: Timer, A: Order](as: List[A]): F[List[A]] =
    Trace[F].span(as.mkString(",")) {
      Timer[F].sleep(10.milli) *> {
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

  def runF[F[_]: Sync: Trace: Parallel: Timer]: F[Unit] =
    Trace[F].span("Sort some stuff!") {
      for {
        as <- Sync[F].delay(List.fill(100)(Random.nextInt(1000)))
        _ <- qsort[F, Int](as)
        _ <- convertedTrace[F]
      } yield ()
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      blocker <- Blocker[Task]
      implicit0(logger: Logger[Task]) <- Resource.liftF(Slf4jLogger.create[Task])
      ep <- entryPoint[Task](blocker, TraceProcess("trace4cats"))
    } yield ep)
      .use { ep =>
        ep.root("this is the root span").use { span =>
          runF[ZIO[Span[Task], Throwable, *]].provide(span)
        }
      }
      .as(ExitCode.success)
      .run
      .map {
        case Exit.Success(value) => value
        case Exit.Failure(_) => ExitCode.failure
      }
}
