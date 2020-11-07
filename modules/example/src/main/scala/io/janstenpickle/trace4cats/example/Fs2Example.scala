package io.janstenpickle.trace4cats.example

import java.util.concurrent.TimeUnit

import cats.data.{Kleisli, WriterT}
import cats.effect.{Blocker, Bracket, Clock, Concurrent, ContextShift, ExitCode, IO, IOApp, Resource, Sync, Timer}
import cats.implicits._
import cats.{Applicative, Functor, Monad, Order, Parallel}
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.fs2.{EntryPointStream, Fs2EntryPoint, TracedStream}
import io.janstenpickle.trace4cats.fs2.syntax.all._
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.AttributeValue.LongValue
import io.janstenpickle.trace4cats.model.{SpanContext, SpanKind, TraceProcess}

import scala.concurrent.duration._
import scala.util.Random

object Fs2Example extends IOApp {

  def entryPoint[F[_]: Concurrent: ContextShift: Timer: Parallel: Logger](
    blocker: Blocker,
    process: TraceProcess
  ): Resource[F, Fs2EntryPoint[F]] =
    AvroSpanCompleter.udp[F](blocker, process, batchTimeout = 50.millis).map { completer =>
      Fs2EntryPoint[F](SpanSampler.probabilistic[F](0.05), completer)
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

  def runF[F[_]: Sync: Trace: Parallel: Timer](timeMs: String): F[Unit] =
    Trace[F].span("Sort some stuff!") {
      for {
        _ <- Sync[F].delay(println(timeMs))
        as <- Sync[F].delay(List.fill(100)(Random.nextInt(1000)))
        _ <- qsort[F, Int](as)
      } yield ()
    }

  def sourceStream[F[_]: Functor: Timer]: Stream[F, FiniteDuration] = Stream.awakeEvery[F](10.seconds)

  // uses WriterT to inject the Fs2EntryPoint with element in the stream
  def inject[F[_]: Functor: Timer](ep: Fs2EntryPoint[F]): EntryPointStream[F, FiniteDuration] =
    sourceStream[F].inject(ep)

  // after the first call to `evalMap` a `SpanContext` is propagated alongside the entry point
  def doWork[F[_]: Bracket[*[_], Throwable]: Clock](
    stream: EntryPointStream[F, FiniteDuration]
  ): TracedStream[F, Long] =
    stream
      // eval some effect within a span
      .evalMap("this is the root span", SpanKind.Consumer, "optional-attribute" -> true) { dur =>
        Clock[F].realTime(TimeUnit.MILLISECONDS).map(_ + dur.toMillis)
      }

  // perform a map operation on the underlying stream where each element is traced
  def map[F[_]: Bracket[*[_], Throwable]: Clock](stream: TracedStream[F, Long]): TracedStream[F, String] =
    stream.traceMapChunk("map", "opt-attr-2" -> LongValue(1))(_.toString)

  // `evalMapTrace` takes a function which transforms A => Kleisli[F, Span[F], B] and injects a root or child span
  // to the evaluation. This allows implicit resolution of the `Trace` typeclass in any methods accessed within the
  // evaluation
  def doTracedWork[F[_]: Sync: Parallel: Timer](
    stream: WriterT[Stream[F, *], (Fs2EntryPoint[F], SpanContext), String]
  ): TracedStream[F, Unit] =
    stream.evalMapTrace("this is a child span of the root", SpanKind.Consumer) { time =>
      runF[Kleisli[F, Span[F], *]](time)
    }

  // gets the trace headers from the span context so that they may be propagated across service boundaries
  def getHeaders[F[_]](
    stream: WriterT[Stream[F, *], (Fs2EntryPoint[F], SpanContext), Unit]
  ): Stream[F, (Map[String, String], Unit)] =
    stream.traceHeaders

  def continue[F[_]: Bracket[*[_], Throwable]](
    ep: Fs2EntryPoint[F],
    stream: Stream[F, (Map[String, String], Unit)]
  ): WriterT[Stream[F, *], (Fs2EntryPoint[F], SpanContext), Unit] =
    // inject the entry point and extract headers from the stream element
    stream
      .injectContinue(ep)(_._1)
      .evalMap("child span in new service", SpanKind.Consumer) { _ =>
        Applicative[F].unit
      }

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]
      implicit0(logger: Logger[IO]) <- Resource.liftF(Slf4jLogger.create[IO])
      ep <- entryPoint[IO](blocker, TraceProcess("trace4catsFS2"))
    } yield ep)
      .use { ep =>
        // inject the entry point into an infinite stream, do some work,
        // then export the trace context as message headers
        val headersStream = getHeaders(
          inject(ep)
            .through(doWork[IO])
            .through(map[IO])
            .through(doTracedWork[IO])
        )

        // simulate going across service boundaries by using the message headers
        val continuedStream = continue(ep, headersStream)

        continuedStream.run.compile.drain
      }
      .as(ExitCode.Success)
}
