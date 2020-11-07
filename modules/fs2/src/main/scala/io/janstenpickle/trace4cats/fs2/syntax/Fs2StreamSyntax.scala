package io.janstenpickle.trace4cats.fs2.syntax

import cats.data.WriterT
import cats.effect.{Bracket, Resource}
import cats.syntax.apply._
import cats.syntax.functor._
import cats.{~>, Applicative, Defer}
import fs2.Stream
import io.janstenpickle.trace4cats.fs2.{EntryPointStream, Fs2EntryPoint, TraceHeadersStream, TracedStream}
import io.janstenpickle.trace4cats.inject.Provide
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanContext, SpanKind}
import io.janstenpickle.trace4cats.{Span, ToHeaders}

trait Fs2StreamSyntax {
  implicit class InjectEntryPoint[F[_], A](stream: Stream[F, A]) {
    def inject(ep: Fs2EntryPoint[F]): EntryPointStream[F, A] = WriterT(stream.map(ep -> _))
    def injectContinue(ep: Fs2EntryPoint[F])(f: A => Map[String, String]): TraceHeadersStream[F, A] =
      WriterT(stream.map(a => (ep, f(a)) -> a))
  }

  trait EvalOps[F[_], G[_], L, A] {
    protected def stream: WriterT[Stream[F, *], L, A]

    protected def makeSpan[B](name: String, kind: SpanKind, l: L)(implicit
      F: Applicative[F]
    ): Resource[F, (Fs2EntryPoint[F], Span[F])]

    private def evalTrace[B](name: String, kind: SpanKind)(
      f: A => G[B]
    )(implicit F: Bracket[F, Throwable], provide: Provide[F, G]): (L, A) => F[((Fs2EntryPoint[F], SpanContext), B)] = {
      case (l, a) =>
        makeSpan(name, kind, l).use {
          case (ep, span) => provide(f(a))(span).map((ep, span.context) -> _)
        }
    }

    private def eval[B](name: String, kind: SpanKind, attributes: (String, AttributeValue)*)(
      f: A => F[B]
    )(implicit F: Bracket[F, Throwable]): (L, A) => F[((Fs2EntryPoint[F], SpanContext), B)] = {
      case (l, a) =>
        makeSpan(name, kind, l).use {
          case (ep, span) => span.putAll(attributes: _*) *> f(a).map((ep, span.context) -> _)
        }
    }

    def through[B](f: WriterT[Stream[F, *], L, A] => TracedStream[F, B]): TracedStream[F, B] = f(stream)

    def evalMapTrace[B](
      name: String
    )(f: A => G[B])(implicit F: Bracket[F, Throwable], provide: Provide[F, G]): TracedStream[F, B] =
      evalMapTrace(name, SpanKind.Internal)(f)

    def evalMapTrace[B](name: String, kind: SpanKind)(
      f: A => G[B]
    )(implicit F: Bracket[F, Throwable], provide: Provide[F, G]): TracedStream[F, B] =
      WriterT(stream.run.evalMap(evalTrace(name, kind)(f).tupled))

    def evalMapChunkTrace[B](
      name: String
    )(f: A => G[B])(implicit F: Bracket[F, Throwable], provide: Provide[F, G]): TracedStream[F, B] =
      evalMapChunkTrace(name, SpanKind.Internal)(f)

    def evalMapChunkTrace[B](name: String, kind: SpanKind)(
      f: A => G[B]
    )(implicit F: Bracket[F, Throwable], provide: Provide[F, G]): TracedStream[F, B] =
      WriterT(stream.run.evalMapChunk(evalTrace(name, kind)(f).tupled))

    def evalMap[B](name: String, attributes: (String, AttributeValue)*)(f: A => F[B])(implicit
      F: Bracket[F, Throwable]
    ): TracedStream[F, B] = evalMap(name, SpanKind.Internal, attributes: _*)(f)

    def evalMap[B](name: String, kind: SpanKind, attributes: (String, AttributeValue)*)(
      f: A => F[B]
    )(implicit F: Bracket[F, Throwable]): TracedStream[F, B] =
      WriterT(stream.run.evalMap(eval(name, kind, attributes: _*)(f).tupled))

    def evalMapChunk[B](name: String, attributes: (String, AttributeValue)*)(
      f: A => F[B]
    )(implicit F: Bracket[F, Throwable]): TracedStream[F, B] =
      evalMapChunk(name, SpanKind.Internal, attributes: _*)(f)

    def evalMapChunk[B](name: String, kind: SpanKind, attributes: (String, AttributeValue)*)(
      f: A => F[B]
    )(implicit F: Bracket[F, Throwable]): TracedStream[F, B] =
      WriterT(stream.run.evalMapChunk(eval(name, kind, attributes: _*)(f).tupled))

    def traceMapChunk[B](name: String, attributes: (String, AttributeValue)*)(
      f: A => B
    )(implicit F: Bracket[F, Throwable]): TracedStream[F, B] =
      traceMapChunk[B](name, SpanKind.Internal, attributes: _*)(f)

    def traceMapChunk[B](name: String, kind: SpanKind, attributes: (String, AttributeValue)*)(
      f: A => B
    )(implicit F: Bracket[F, Throwable]): TracedStream[F, B] =
      WriterT(stream.run.evalMapChunk(eval(name, kind, attributes: _*)(a => Applicative[F].pure(f(a))).tupled))

    def dropTrace: Stream[F, A] = stream.run.map(_._2)
  }

  implicit class RootTrace[F[_], G[_], A](override val stream: EntryPointStream[F, A])
      extends EvalOps[F, G, Fs2EntryPoint[F], A] {
    override protected def makeSpan[B](name: String, kind: SpanKind, l: Fs2EntryPoint[F])(implicit
      F: Applicative[F]
    ): Resource[F, (Fs2EntryPoint[F], Span[F])] = l.root(name, kind).map(l -> _)

    def translate[H[_], M](fk: F ~> H)(implicit G: Applicative[H], defer: Defer[H]): EntryPointStream[H, A] =
      WriterT(stream.run.translate(fk).map { case (ep, a) => ep.mapK(fk) -> a })
  }

  implicit class ContinueOrElseRoot[F[_], G[_], A](override val stream: TraceHeadersStream[F, A])
      extends EvalOps[F, G, (Fs2EntryPoint[F], Map[String, String]), A] {
    override protected def makeSpan[B](name: String, kind: SpanKind, l: (Fs2EntryPoint[F], Map[String, String]))(
      implicit F: Applicative[F]
    ): Resource[F, (Fs2EntryPoint[F], Span[F])] =
      l match {
        case (ep, headers) => ep.continueOrElseRoot(name, kind, headers).map(ep -> _)
      }

    def translate[H[_], M](
      fk: F ~> H
    )(implicit G: Applicative[H], defer: Defer[H]): WriterT[Stream[H, *], (Fs2EntryPoint[H], Map[String, String]), A] =
      WriterT(stream.run.translate(fk).map { case ((ep, headers), a) => (ep.mapK(fk), headers) -> a })
  }

  implicit class Continue[F[_], G[_], A](override val stream: TracedStream[F, A])
      extends EvalOps[F, G, (Fs2EntryPoint[F], SpanContext), A] {
    override protected def makeSpan[B](name: String, kind: SpanKind, l: (Fs2EntryPoint[F], SpanContext))(implicit
      F: Applicative[F]
    ): Resource[F, (Fs2EntryPoint[F], Span[F])] =
      l match {
        case (ep, context) => ep.continue(name, kind, context).map(ep -> _)
      }

    def translate[H[_], M](fk: F ~> H)(implicit G: Applicative[H], defer: Defer[H]): TracedStream[H, A] =
      WriterT(stream.run.translate(fk).map { case ((ep, context), a) => (ep.mapK(fk), context) -> a })

    def traceHeaders: Stream[F, (Map[String, String], A)] = traceHeaders(ToHeaders.all)
    def traceHeaders(toHeaders: ToHeaders): Stream[F, (Map[String, String], A)] =
      stream.run.map {
        case ((_, context), a) => toHeaders.fromContext(context) -> a
      }

    def mapTraceHeaders[B](f: (Map[String, String], A) => B): Stream[F, B] = mapTraceHeaders[B](ToHeaders.all)(f)
    def mapTraceHeaders[B](toHeaders: ToHeaders)(f: (Map[String, String], A) => B): Stream[F, B] =
      stream.run.map {
        case ((_, context), a) => f(toHeaders.fromContext(context), a)
      }

    def evalMapTraceHeaders[B](f: (Map[String, String], A) => F[B])(implicit F: Bracket[F, Throwable]): Stream[F, B] =
      evalMapTraceHeaders(ToHeaders.all)(f)
    def evalMapTraceHeaders[B](
      toHeaders: ToHeaders
    )(f: (Map[String, String], A) => F[B])(implicit F: Bracket[F, Throwable]): Stream[F, B] =
      stream.run.evalMap {
        case ((ep, context), a) =>
          ep.continue("propagate", SpanKind.Producer, context).use(_ => f(toHeaders.fromContext(context), a))
      }
  }
}
