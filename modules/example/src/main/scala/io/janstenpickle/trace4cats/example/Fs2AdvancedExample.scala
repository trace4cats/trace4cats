// TODO move to separate example repo

//package io.janstenpickle.trace4cats.example
//
//import cats.data.Kleisli
//import cats.effect.kernel.{Async, Clock, Resource, Temporal}
//import cats.effect.std.{Console, Random}
//import cats.effect.{ExitCode, IO, IOApp, MonadCancelThrow}
//import cats.implicits._
//import cats.{Applicative, Apply, Functor, Monad, Order, Parallel}
//import fs2.Stream
//import io.janstenpickle.trace4cats.Span
//import io.janstenpickle.trace4cats.`export`.CompleterConfig
//import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
//import io.janstenpickle.trace4cats.base.context.Provide
//import io.janstenpickle.trace4cats.fs2.TracedStream
//import io.janstenpickle.trace4cats.fs2.syntax.all._
//import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
//import io.janstenpickle.trace4cats.kernel.SpanSampler
//import io.janstenpickle.trace4cats.model.AttributeValue.LongValue
//import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders, TraceProcess}
//
//import scala.concurrent.duration._
//
//object Fs2AdvancedExample extends IOApp {
//
//  def entryPoint[F[_]: Async](process: TraceProcess): Resource[F, EntryPoint[F]] =
//    AvroSpanCompleter.udp[F](process, config = CompleterConfig(batchTimeout = 50.millis)).map { completer =>
//      EntryPoint[F](SpanSampler.probabilistic[F](0.05), completer)
//    }
//
//  // Intentionally slow parallel quicksort, to demonstrate branching. If we run too quickly it seems
//  // to break Jaeger with "skipping clock skew adjustment" so let's pause a bit each time.
//  def qsort[F[_]: Temporal: Parallel: Trace, A: Order](as: List[A]): F[List[A]] =
//    Trace[F].span(as.mkString(",")) {
//      Temporal[F].sleep(10.milli) *> {
//        as match {
//          case Nil => Monad[F].pure(Nil)
//          case h :: t =>
//            val (a, b) = t.partition(_ <= h)
//            (qsort[F, A](a), qsort[F, A](b)).parMapN(_ ++ List(h) ++ _)
//        }
//      }
//    }
//
//  def runF[F[_]: Temporal: Console: Random: Trace: Parallel](timeMs: String): F[Unit] =
//    Trace[F].span("Sort some stuff!") {
//      for {
//        _ <- Console[F].println(timeMs)
//        as <- Random[F].nextIntBounded(1000).replicateA(100)
//        _ <- qsort[F, Int](as)
//      } yield ()
//    }
//
//  def sourceStream[F[_]: Temporal]: Stream[F, FiniteDuration] = Stream.awakeEvery[F](10.seconds)
//
//  // uses WriterT to inject the EntryPoint with element in the stream
//  def inject[F[_]: Temporal](ep: EntryPoint[F]): TracedStream[F, FiniteDuration] =
//    sourceStream[F].inject(ep, "this is injected root span", SpanKind.Producer)
//
//  // after the first call to `evalMap` a `Span` is propagated alongside the entry point
//  def doWork[F[_]: Apply: Clock: Trace](stream: TracedStream[F, FiniteDuration]): TracedStream[F, Long] =
//    stream
//      // eval some traced effect
//      .evalMap { dur =>
//        Trace[F].span("this is child of the initial injected root span", SpanKind.Internal) {
//          Trace[F].put("optional-attribute", true) *> Clock[F].realTime.map(t => (t + dur).toMillis)
//        }
//      }
//
//  // perform a map operation on the underlying stream where each element is traced
//  def map[F[_]: Functor: Trace](stream: TracedStream[F, Long]): TracedStream[F, String] =
//    stream.evalMap { long =>
//      Trace[F].span("map") {
//        Trace[F].put("opt-attr-2", LongValue(long)).as(long.toString)
//      }
//    }
//
//  // `evalMapTrace` takes a function which transforms A => Kleisli[F, Span[F], B] and injects a root or child span
//  // to the evaluation. This allows implicit resolution of the `Trace` typeclass in any methods accessed within the
//  // evaluation
//  def doTracedWork[F[_]: Temporal: Console: Random: Trace: Parallel](
//    stream: TracedStream[F, String]
//  ): TracedStream[F, Unit] =
//    stream.evalMap { time =>
//      runF[F](time)
//    }
//
//  // gets the trace headers from the span context so that they may be propagated across service boundaries
//  def getHeaders[F[_]](stream: TracedStream[F, Unit]): TracedStream[F, (TraceHeaders, Unit)] =
//    stream.traceHeaders
//
//  def continue[F[_]: MonadCancelThrow, G[_]: MonadCancelThrow: Trace](
//    ep: EntryPoint[F],
//    stream: Stream[F, (TraceHeaders, Unit)]
//  )(implicit P: Provide[F, G, Span[F]]): TracedStream[G, Unit] =
//    // inject the entry point and extract headers from the stream element
//    stream
//      .injectContinue(ep, "this is the root span in a new service", SpanKind.Consumer)(_._1)
//      .liftTrace[G] // lift the stream into the traced effect "G"
//      .evalMap { _ =>
//        // Perform a traced operation within the stream
//        Trace[G].span("child span in new service", SpanKind.Consumer)(Applicative[G].unit)
//      }
//
//  override def run(args: List[String]): IO[ExitCode] =
//    entryPoint[IO](TraceProcess("trace4catsFS2"))
//      .use { ep =>
//        implicit val random: Random[Kleisli[IO, Span[IO], *]] = Random.javaUtilConcurrentThreadLocalRandom
//
//        // inject the entry point into an infinite stream, do some work,
//        // then export the trace context as message headers
//        val headersStream: TracedStream[Kleisli[IO, Span[IO], *], (TraceHeaders, Unit)] =
//          inject(ep)
//            .liftTrace[Kleisli[IO, Span[IO], *]] // lift the stream effect to the traced type
//            .through(doWork[Kleisli[IO, Span[IO], *]])
//            .through(map[Kleisli[IO, Span[IO], *]])
//            .through(doTracedWork[Kleisli[IO, Span[IO], *]])
//            .through(getHeaders[Kleisli[IO, Span[IO], *]])
//
//        val headers: Stream[IO, (TraceHeaders, Unit)] =
//          headersStream.endTrace[IO] // `endTrace[IO]` returns the stream's effect to IO by providing a "noop" span
//
//        // simulate going across service boundaries by using the message headers
//        val continuedStream = continue[IO, Kleisli[IO, Span[IO], *]](ep, headers)
//
//        continuedStream.endTrace[IO].compile.drain
//      }
//      .as(ExitCode.Success)
//}
