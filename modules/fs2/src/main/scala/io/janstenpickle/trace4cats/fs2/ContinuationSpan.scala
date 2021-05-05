package io.janstenpickle.trace4cats.fs2

import cats.data.NonEmptyList
import cats.effect.Resource
import cats.{Applicative, Defer}
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.{ErrorHandler, Span}

trait ContinuationSpan[F[_]] extends Span[F] {
  def run[A](k: F[A]): F[A]
}

object ContinuationSpan {
  def fromSpan[F[_], G[_]: Applicative: Defer](
    span: Span[F]
  )(implicit P: Provide[F, G, Span[F]]): ContinuationSpan[G] = {
    // 👀
    val spanK: Span[G] = span.mapK(P.liftK)

    new ContinuationSpan[G] {
      override def context: SpanContext = spanK.context

      override def put(key: String, value: AttributeValue): G[Unit] = spanK.put(key, value)

      override def putAll(fields: (String, AttributeValue)*): G[Unit] = spanK.putAll(fields: _*)

      override def putAll(fields: Map[String, AttributeValue]): G[Unit] = spanK.putAll(fields)

      override def setStatus(spanStatus: SpanStatus): G[Unit] = spanK.setStatus(spanStatus)

      override def addLink(link: Link): G[Unit] = spanK.addLink(link)

      override def addLinks(links: NonEmptyList[Link]): G[Unit] = spanK.addLinks(links)

      override def child(name: String, kind: SpanKind): Resource[G, Span[G]] = spanK.child(name, kind)

      override def child(name: String, kind: SpanKind, errorHandler: ErrorHandler): Resource[G, Span[G]] =
        spanK.child(name, kind, errorHandler)

      override def run[A](k: G[A]): G[A] = P.lift(P.provide(k)(span))
    }
  }
}
