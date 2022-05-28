package trace4cats.fs2.syntax

import cats.data.WriterT
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.functor._
import cats.{~>, Applicative, Functor}
import fs2.Stream
import trace4cats.context.Provide
import trace4cats.fs2.{ContinuationSpan, TracedStream}
import trace4cats.kernel.{ErrorHandler, Span, ToHeaders}
import trace4cats.model.{AttributeValue, SpanKind, TraceHeaders}
import trace4cats.{EntryPoint, ResourceKleisli, SpanName, SpanParams}

trait Fs2StreamSyntax {
  implicit class InjectEntryPoint[F[_]: MonadCancelThrow, A](stream: Stream[F, A]) {
    def inject(ep: EntryPoint[F], name: String): TracedStream[F, A] =
      inject(ep, _ => name, SpanKind.Internal)

    def trace(k: ResourceKleisli[F, SpanParams, Span[F]], name: SpanName): TracedStream[F, A] =
      trace(k, _ => name, SpanKind.Internal)

    def inject(ep: EntryPoint[F], name: A => String): TracedStream[F, A] =
      inject(ep, name, SpanKind.Internal)

    def trace(k: ResourceKleisli[F, SpanParams, Span[F]], name: A => SpanName): TracedStream[F, A] =
      trace(k, name, SpanKind.Internal)

    def inject(ep: EntryPoint[F], name: SpanName, kind: SpanKind): TracedStream[F, A] =
      inject(ep, _ => name, kind)

    def trace(k: ResourceKleisli[F, SpanParams, Span[F]], name: SpanName, kind: SpanKind): TracedStream[F, A] =
      trace(k, _ => name, kind)

    def inject(ep: EntryPoint[F], name: A => SpanName, kind: SpanKind): TracedStream[F, A] =
      trace(ep.toKleisli, name, kind)

    def trace(k: ResourceKleisli[F, SpanParams, Span[F]], name: A => SpanName, kind: SpanKind): TracedStream[F, A] =
      trace(k, name, kind, ErrorHandler.empty)

    def trace(
      k: ResourceKleisli[F, SpanParams, Span[F]],
      name: A => SpanName,
      kind: SpanKind,
      errorHandler: ErrorHandler
    ): TracedStream[F, A] =
      WriterT(stream.evalMapChunk(a => k((name(a), kind, TraceHeaders.empty, errorHandler)).use(s => (s -> a).pure)))

    def injectContinue(ep: EntryPoint[F], name: String)(f: A => TraceHeaders): TracedStream[F, A] =
      injectContinue(ep, name, SpanKind.Internal)(f)

    def traceContinue(k: ResourceKleisli[F, SpanParams, Span[F]], name: SpanName)(
      f: A => TraceHeaders
    ): TracedStream[F, A] =
      traceContinue(k, name, SpanKind.Internal)(f)

    def injectContinue(ep: EntryPoint[F], name: SpanName, kind: SpanKind)(f: A => TraceHeaders): TracedStream[F, A] =
      injectContinue(ep, _ => name, kind)(f)

    def traceContinue(k: ResourceKleisli[F, SpanParams, Span[F]], name: SpanName, kind: SpanKind)(
      f: A => TraceHeaders
    ): TracedStream[F, A] =
      traceContinue(k, _ => name, kind)(f)

    def injectContinue(ep: EntryPoint[F], name: A => SpanName)(f: A => TraceHeaders): TracedStream[F, A] =
      injectContinue(ep, name, SpanKind.Internal)(f)

    def traceContinue(k: ResourceKleisli[F, SpanParams, Span[F]], name: A => SpanName)(
      f: A => TraceHeaders
    ): TracedStream[F, A] =
      traceContinue(k, name, SpanKind.Internal)(f)

    def injectContinue(ep: EntryPoint[F], name: A => SpanName, kind: SpanKind)(
      f: A => TraceHeaders
    ): TracedStream[F, A] =
      traceContinue(ep.toKleisli, name, kind)(f)

    def traceContinue(k: ResourceKleisli[F, SpanParams, Span[F]], name: A => SpanName, kind: SpanKind)(
      f: A => TraceHeaders
    ): TracedStream[F, A] = traceContinue(k, name, kind, ErrorHandler.empty)(f)

    def traceContinue(
      k: ResourceKleisli[F, SpanParams, Span[F]],
      name: A => SpanName,
      kind: SpanKind,
      errorHandler: ErrorHandler
    )(f: A => TraceHeaders): TracedStream[F, A] =
      WriterT(stream.evalMapChunk(a => k((name(a), kind, f(a), errorHandler)).use(s => (s -> a).pure)))

  }

  implicit class TracedStreamOps[F[_], A](stream: TracedStream[F, A]) {

    private def eval[B](f: A => F[B])(implicit F: Functor[F]): (Span[F], A) => F[(Span[F], B)] = { case (span, a) =>
      span match {
        case s: ContinuationSpan[F] => s.run(f(a)).map(span -> _)
        case _ => f(a).map(span -> _)
      }
    }

    private def eval[B](name: String, kind: SpanKind, attributes: (String, AttributeValue)*)(f: A => F[B])(implicit
      F: MonadCancelThrow[F]
    ): (Span[F], A) => F[(Span[F], B)] = { case (span, a) =>
      span.child(name, kind).use { child =>
        child.putAll(attributes: _*) *> eval(f).apply(span, a)
      }
    }

    private def evalTrace[G[_], B](
      f: A => G[B]
    )(implicit F: Functor[F], P: Provide[F, G, Span[F]]): (Span[F], A) => F[(Span[F], B)] = { case (span, a) =>
      P.provide(f(a))(span).map(span -> _)
    }

    def evalMapTrace[G[_], B](f: A => G[B])(implicit F: Functor[F], P: Provide[F, G, Span[F]]): TracedStream[F, B] =
      WriterT(stream.run.evalMap(evalTrace(f).tupled))

    def evalMapChunkTrace[G[_], B](
      f: A => G[B]
    )(implicit F: Applicative[F], P: Provide[F, G, Span[F]]): TracedStream[F, B] =
      WriterT(stream.run.evalMapChunk(evalTrace(f).tupled))

    def evalMap[B](f: A => F[B])(implicit F: Functor[F]): TracedStream[F, B] =
      WriterT(stream.run.evalMap(eval(f).tupled))

    def evalMapChunk[B](f: A => F[B])(implicit F: Applicative[F]): TracedStream[F, B] =
      WriterT(stream.run.evalMapChunk(eval(f).tupled))

    def evalMap[B](name: String, attributes: (String, AttributeValue)*)(f: A => F[B])(implicit
      F: MonadCancelThrow[F]
    ): TracedStream[F, B] =
      evalMap(name, SpanKind.Internal, attributes: _*)(f)

    def evalMap[B](name: String, kind: SpanKind, attributes: (String, AttributeValue)*)(f: A => F[B])(implicit
      F: MonadCancelThrow[F]
    ): TracedStream[F, B] =
      WriterT(stream.run.evalMap(eval(name, kind, attributes: _*)(f).tupled))

    def evalMapChunk[B](name: String, attributes: (String, AttributeValue)*)(f: A => F[B])(implicit
      F: MonadCancelThrow[F]
    ): TracedStream[F, B] =
      evalMapChunk(name, SpanKind.Internal, attributes: _*)(f)

    def evalMapChunk[B](name: String, kind: SpanKind, attributes: (String, AttributeValue)*)(f: A => F[B])(implicit
      F: MonadCancelThrow[F]
    ): TracedStream[F, B] =
      WriterT(stream.run.evalMapChunk(eval(name, kind, attributes: _*)(f).tupled))

    def traceMapChunk[B](name: String, attributes: (String, AttributeValue)*)(f: A => B)(implicit
      F: MonadCancelThrow[F]
    ): TracedStream[F, B] =
      traceMapChunk[B](name, SpanKind.Internal, attributes: _*)(f)

    def traceMapChunk[B](name: String, kind: SpanKind, attributes: (String, AttributeValue)*)(f: A => B)(implicit
      F: MonadCancelThrow[F]
    ): TracedStream[F, B] =
      WriterT(stream.run.evalMapChunk(eval(name, kind, attributes: _*)(a => Applicative[F].pure(f(a))).tupled))

    def endTrace: Stream[F, A] =
      stream.value

    def endTrace[G[_]: MonadCancelThrow](implicit F: MonadCancelThrow[F], P: Provide[G, F, Span[G]]): Stream[G, A] =
      Stream.resource(Span.noop[G]).flatMap(endTrace(_))

    def endTrace[G[_]: MonadCancelThrow](
      span: Span[G]
    )(implicit F: MonadCancelThrow[F], P: Provide[G, F, Span[G]]): Stream[G, A] =
      translate(P.provideK(span)).value

    def through[B](f: TracedStream[F, A] => TracedStream[F, B]): TracedStream[F, B] = f(stream)

    def liftTrace[G[_]: MonadCancelThrow](implicit
      F: MonadCancelThrow[F],
      P: Provide[F, G, Span[F]]
    ): TracedStream[G, A] =
      WriterT(stream.run.translate(P.liftK).map { case (span, a) =>
        ContinuationSpan.fromSpan[F, G](span) -> a
      })

    def translate[G[_]: MonadCancelThrow](fk: F ~> G)(implicit F: MonadCancelThrow[F]): TracedStream[G, A] =
      WriterT(stream.run.translate(fk).map { case (span, a) =>
        span.mapK(fk) -> a
      })

    def traceHeaders: TracedStream[F, (TraceHeaders, A)] = traceHeaders(ToHeaders.standard)

    def traceHeaders(toHeaders: ToHeaders): TracedStream[F, (TraceHeaders, A)] =
      WriterT(stream.run.map { case (span, a) =>
        (span, (toHeaders.fromContext(span.context), a))
      })

    def mapTraceHeaders[B](f: (TraceHeaders, A) => B): TracedStream[F, B] =
      mapTraceHeaders[B](ToHeaders.standard)(f)

    def mapTraceHeaders[B](toHeaders: ToHeaders)(f: (TraceHeaders, A) => B): TracedStream[F, B] =
      WriterT(stream.run.map { case (span, a) =>
        span -> f(toHeaders.fromContext(span.context), a)
      })

    def evalMapTraceHeaders[B](f: (TraceHeaders, A) => F[B])(implicit F: Functor[F]): TracedStream[F, B] =
      evalMapTraceHeaders(ToHeaders.standard)(f)

    def evalMapTraceHeaders[B](toHeaders: ToHeaders)(f: (TraceHeaders, A) => F[B])(implicit
      F: Functor[F]
    ): TracedStream[F, B] =
      WriterT(stream.run.evalMap { case (span, a) => f(toHeaders.fromContext(span.context), a).map(span -> _) })

  }

}
