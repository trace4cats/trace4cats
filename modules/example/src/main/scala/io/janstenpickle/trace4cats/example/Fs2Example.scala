// TODO move to separate example repo

//package io.janstenpickle.trace4cats.example
//
//import cats.data.Kleisli
//import cats.effect.{ExitCode, IO, IOApp, MonadCancelThrow}
//import cats.effect.kernel.{Async, Clock, Resource, Temporal}
//import cats.effect.std.{Console, Random}
//import cats.implicits._
//import cats.{Applicative, Monad, Order, Parallel}
//import fs2.Stream
//import io.janstenpickle.trace4cats.Span
//import io.janstenpickle.trace4cats.`export`.CompleterConfig
//import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
//import io.janstenpickle.trace4cats.fs2.TracedStream
//import io.janstenpickle.trace4cats.fs2.syntax.all._
//import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
//import io.janstenpickle.trace4cats.kernel.SpanSampler
//import io.janstenpickle.trace4cats.model.AttributeValue.{BooleanValue, LongValue}
//import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders, TraceProcess}
//
//import scala.concurrent.duration._
//
//object Fs2Example extends IOApp {
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
//  def doWork[F[_]: MonadCancelThrow: Clock](stream: TracedStream[F, FiniteDuration]): TracedStream[F, Long] =
//    stream
//      // eval some effect within a span
//      .evalMap(
//        "this is child of the initial injected root span",
//        SpanKind.Internal,
//        "optional-attribute" -> BooleanValue(true)
//      ) { dur =>
//        Clock[F].realTime.map(t => (t + dur).toMillis)
//      }
//
//  // perform a map operation on the underlying stream where each element is traced
//  def map[F[_]: MonadCancelThrow](stream: TracedStream[F, Long]): TracedStream[F, String] =
//    stream.traceMapChunk("map", "opt-attr-2" -> LongValue(1))(_.toString)
//
//  // `evalMapTrace` takes a function which transforms A => Kleisli[F, Span[F], B] and injects a root or child span
//  // to the evaluation. This allows implicit resolution of the `Trace` typeclass in any methods accessed within the
//  // evaluation
//  def doTracedWork[F[_]: Async: Parallel](stream: TracedStream[F, String]): TracedStream[F, Unit] =
//    stream.evalMapTrace { time =>
//      type G[x] = Kleisli[F, Span[F], x]
//      implicit val random: Random[G] = Random.javaUtilConcurrentThreadLocalRandom
//      implicit val console: Console[G] = Console.make[G]
//      runF[G](time)
//    }
//
//  // gets the trace headers from the span context so that they may be propagated across service boundaries
//  def getHeaders[F[_]](stream: TracedStream[F, Unit]): Stream[F, (TraceHeaders, Unit)] =
//    stream.traceHeaders.endTrace
//
//  def continue[F[_]: MonadCancelThrow](
//    ep: EntryPoint[F],
//    stream: Stream[F, (TraceHeaders, Unit)]
//  ): TracedStream[F, Unit] =
//    // inject the entry point and extract headers from the stream element
//    stream
//      .injectContinue(ep, "this is the root span in a new service", SpanKind.Consumer)(_._1)
//      .evalMap("child span in new service", SpanKind.Consumer) { _ =>
//        Applicative[F].unit
//      }
//
//  override def run(args: List[String]): IO[ExitCode] =
//    entryPoint[IO](TraceProcess("trace4catsFS2"))
//      .use { ep =>
//        // inject the entry point into an infinite stream, do some work,
//        // then export the trace context as message headers
//        val headersStream = getHeaders(
//          inject(ep)
//            .through(doWork[IO])
//            .through(map[IO])
//            .through(doTracedWork[IO])
//        )
//
//        // simulate going across service boundaries by using the message headers
//        val continuedStream = continue(ep, headersStream)
//
//        continuedStream.run.compile.drain
//      }
//      .as(ExitCode.Success)
//}
