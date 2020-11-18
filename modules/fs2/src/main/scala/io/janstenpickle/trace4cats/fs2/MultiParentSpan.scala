package io.janstenpickle.trace4cats.fs2

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.Resource
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.model._

case class MultiParentSpan[F[_]: Applicative](parent: Span[F], parents: List[SpanContext] = List.empty)
    extends ContinuationSpan[F] {
  override def context: SpanContext = parent.context

  override def put(key: String, value: AttributeValue): F[Unit] = parent.put(key, value)

  override def putAll(fields: (String, AttributeValue)*): F[Unit] = parent.putAll(fields: _*)

  override def setStatus(spanStatus: SpanStatus): F[Unit] = parent.setStatus(spanStatus)

  override def addLink(link: Link): F[Unit] = parent.addLink(link)

  override def addLinks(links: NonEmptyList[Link]): F[Unit] = parent.addLinks(links)

  override def child(name: String, kind: SpanKind): Resource[F, Span[F]] =
    parent
      .child(name, kind)
      .evalTap(span =>
        NonEmptyList
          .fromList(parents)
          .fold(Applicative[F].unit)(ps => span.addLinks(ps.map(p => Link.Parent(p.traceId, p.spanId))))
      )

  override def child(
    name: String,
    kind: SpanKind,
    errorHandler: PartialFunction[Throwable, SpanStatus]
  ): Resource[F, Span[F]] = parent.child(name, kind, errorHandler)

  override def run[A](k: F[A]): F[A] = parent match {
    case s: ContinuationSpan[F] => s.run(k)
    case _ => k
  }
}
