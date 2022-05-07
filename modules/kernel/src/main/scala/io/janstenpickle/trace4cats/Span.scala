package io.janstenpickle.trace4cats

import cats.data.NonEmptyList
import cats.effect.kernel.Resource.ExitCase
import cats.effect.kernel.{Clock, MonadCancelThrow, Ref, Resource}
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{~>, Applicative, Monad}
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model._

import java.time.Instant

trait Span[F[_]] {
  def context: SpanContext
  def put(key: String, value: AttributeValue): F[Unit]
  def putAll(fields: (String, AttributeValue)*): F[Unit]
  def putAll(fields: Map[String, AttributeValue]): F[Unit]
  def setStatus(spanStatus: SpanStatus): F[Unit]
  def addLink(link: Link): F[Unit]
  def addLinks(links: NonEmptyList[Link]): F[Unit]
  def child(name: String, kind: SpanKind): Resource[F, Span[F]]
  def child(name: String, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]]
  final def mapK[G[_]](fk: F ~> G)(implicit F: MonadCancelThrow[F], G: MonadCancelThrow[G]): Span[G] =
    Span.mapK(fk)(this)
}

case class RefSpan[F[_]: Monad: Clock: Ref.Make: SpanId.Gen] private[trace4cats] (
  context: SpanContext,
  name: String,
  kind: SpanKind,
  start: Long,
  attributes: Ref[F, Map[String, AttributeValue]],
  status: Ref[F, SpanStatus],
  links: Ref[F, List[Link]],
  sampler: SpanSampler[F],
  completer: SpanCompleter[F]
) extends Span[F] {

  override def put(key: String, value: AttributeValue): F[Unit] =
    attributes.update(_ + (key -> value))
  override def putAll(fields: (String, AttributeValue)*): F[Unit] =
    attributes.update(_ ++ fields)
  def putAll(fields: Map[String, AttributeValue]): F[Unit] =
    attributes.update(_ ++ fields)
  override def setStatus(spanStatus: SpanStatus): F[Unit] = status.set(spanStatus)
  override def addLink(link: Link): F[Unit] = links.update(link :: _)
  override def addLinks(lns: NonEmptyList[Link]): F[Unit] = links.update(lns.toList ::: _)
  override def child(name: String, kind: SpanKind): Resource[F, Span[F]] =
    Span.child[F](name, context, kind, sampler, completer)
  override def child(name: String, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]] =
    Span.child[F](name, context, kind, sampler, completer, errorHandler)

  private[trace4cats] def end: F[Unit] = status.get.flatMap(end)
  private[trace4cats] def end(status: SpanStatus): F[Unit] =
    for {
      now <- Clock[F].realTimeInstant
      attrs <- attributes.get
      lns <- links.get
      completed = CompletedSpan.Builder(
        context,
        name,
        kind,
        Instant.ofEpochMilli(start),
        now,
        attrs,
        status,
        NonEmptyList.fromList(lns)
      )
      _ <- completer.complete(completed)
    } yield ()

}

case class EmptySpan[F[_]: Applicative: SpanId.Gen] private[trace4cats] (context: SpanContext) extends Span[F] {
  override def put(key: String, value: AttributeValue): F[Unit] = Applicative[F].unit
  override def putAll(fields: (String, AttributeValue)*): F[Unit] = Applicative[F].unit
  override def putAll(fields: Map[String, AttributeValue]): F[Unit] = Applicative[F].unit
  override def setStatus(spanStatus: SpanStatus): F[Unit] = Applicative[F].unit
  override def addLink(link: Link): F[Unit] = Applicative[F].unit
  override def addLinks(links: NonEmptyList[Link]): F[Unit] = Applicative[F].unit
  override def child(name: String, kind: SpanKind): Resource[F, Span[F]] =
    Resource.eval(SpanContext.child[F](context).map { childContext =>
      EmptySpan(childContext.setDrop())
    })

  override def child(name: String, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]] = child(name, kind)
}

case class NoopSpan[F[_]: Applicative] private[trace4cats] (context: SpanContext) extends Span[F] {
  override def put(key: String, value: AttributeValue): F[Unit] = Applicative[F].unit
  override def putAll(fields: (String, AttributeValue)*): F[Unit] = Applicative[F].unit
  override def putAll(fields: Map[String, AttributeValue]): F[Unit] = Applicative[F].unit
  override def setStatus(spanStatus: SpanStatus): F[Unit] = Applicative[F].unit
  override def addLink(link: Link): F[Unit] = Applicative[F].unit
  override def addLinks(links: NonEmptyList[Link]): F[Unit] = Applicative[F].unit
  override def child(name: String, kind: SpanKind): Resource[F, Span[F]] = Span.noop[F]
  override def child(name: String, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]] = child(name, kind)
}

case class DischargeSpan[F[_]: Monad: Clock: Ref.Make: TraceId.Gen: SpanId.Gen] private[trace4cats] (
  context: SpanContext,
  sampler: SpanSampler[F],
  completer: SpanCompleter[F]
) extends Span[F] {
  def put(key: String, value: AttributeValue): F[Unit] = Applicative[F].unit
  def putAll(fields: (String, AttributeValue)*): F[Unit] = Applicative[F].unit
  def putAll(fields: Map[String, AttributeValue]): F[Unit] = Applicative[F].unit
  def setStatus(spanStatus: SpanStatus): F[Unit] = Applicative[F].unit
  def addLink(link: Link): F[Unit] = Applicative[F].unit
  def addLinks(links: NonEmptyList[Link]): F[Unit] = Applicative[F].unit
  def child(name: String, kind: SpanKind): Resource[F, Span[F]] =
    Span.root(name, kind, sampler, completer)
  def child(name: String, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]] =
    Span.root(name, kind, sampler, completer, errorHandler)
}

object Span {
  private def makeSpan[F[_]: Monad: Clock: Ref.Make: SpanId.Gen](
    name: String,
    parent: Option[SpanContext],
    context: SpanContext,
    kind: SpanKind,
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
    errorHandler: ErrorHandler
  ): Resource[F, Span[F]] =
    Resource
      .eval(
        sampler
          .shouldSample(parent, context.traceId, name, kind)
      )
      .flatMap {
        case SampleDecision.Drop =>
          EmptySpan[F](context.setDrop()).pure[Resource[F, *]]
        case SampleDecision.Include =>
          Resource.makeCase(
            for {
              attributesRef <- Ref.of[F, Map[String, AttributeValue]](Map.empty)
              now <- Clock[F].realTime
              statusRef <- Ref.of[F, SpanStatus](SpanStatus.Ok)
              linksRef <- Ref.of[F, List[Link]](List.empty)
            } yield RefSpan[F](
              context,
              name,
              kind,
              now.toMillis,
              attributesRef,
              statusRef,
              linksRef,
              sampler,
              completer
            )
          ) {
            case (span, ExitCase.Succeeded) => span.end
            case (span, ExitCase.Canceled) => span.end(SpanStatus.Cancelled)
            case (span, ExitCase.Errored(th)) =>
              val end = span.end(SpanStatus.Internal(Option(th.getMessage).getOrElse(th.toString)))

              errorHandler.lift(th) match {
                case Some(HandledError.Status(spanStatus)) => span.end(spanStatus)
                case Some(HandledError.Attribute(key, value)) => span.put(key, value) >> end
                case Some(HandledError.Attributes(attributes @ _*)) => span.putAll(attributes: _*) >> end
                case Some(HandledError.StatusAttribute(spanStatus, attributeKey, attributeValue)) =>
                  span.put(attributeKey, attributeValue) >> span.end(spanStatus)
                case Some(HandledError.StatusAttributes(spanStatus, attributes @ _*)) =>
                  span.putAll(attributes: _*) >> span.end(spanStatus)
                case None => end
              }
          }
      }

  def noop[F[_]: Applicative]: Resource[F, Span[F]] = Resource.pure[F, Span[F]](NoopSpan[F](SpanContext.invalid))

  def noopInstance[F[_]: Applicative]: Span[F] = NoopSpan[F](SpanContext.invalid)

  def child[F[_]: Monad: Clock: Ref.Make: SpanId.Gen](
    name: String,
    parent: SpanContext,
    kind: SpanKind,
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
    errorHandler: ErrorHandler = ErrorHandler.empty,
  ): Resource[F, Span[F]] =
    Resource
      .eval(SpanContext.child[F](parent))
      .flatMap(makeSpan(name, Some(parent), _, kind, sampler, completer, errorHandler))

  def root[F[_]: Monad: Clock: Ref.Make: TraceId.Gen: SpanId.Gen](
    name: String,
    kind: SpanKind,
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
    errorHandler: ErrorHandler = ErrorHandler.empty
  ): Resource[F, Span[F]] =
    Resource.eval(SpanContext.root[F]).flatMap(makeSpan(name, None, _, kind, sampler, completer, errorHandler))

  def discharge[F[_]: Monad: Clock: Ref.Make: TraceId.Gen: SpanId.Gen](
    sampler: SpanSampler[F],
    completer: SpanCompleter[F]
  ): F[Span[F]] =
    SpanContext.root[F].map(DischargeSpan(_, sampler, completer))

  private def mapK[F[_]: MonadCancelThrow, G[_]: MonadCancelThrow](fk: F ~> G)(span: Span[F]): Span[G] =
    new Span[G] {
      override def context: SpanContext = span.context
      override def put(key: String, value: AttributeValue): G[Unit] = fk(span.put(key, value))
      override def putAll(fields: (String, AttributeValue)*): G[Unit] = fk(span.putAll(fields: _*))
      override def putAll(fields: Map[String, AttributeValue]): G[Unit] = fk(span.putAll(fields))
      override def setStatus(spanStatus: SpanStatus): G[Unit] = fk(span.setStatus(spanStatus))
      override def addLink(link: Link): G[Unit] = fk(span.addLink(link))
      override def addLinks(links: NonEmptyList[Link]): G[Unit] = fk(span.addLinks(links))
      override def child(name: String, kind: SpanKind): Resource[G, Span[G]] =
        span.child(name, kind).mapK(fk).map(Span.mapK(fk))
      override def child(name: String, kind: SpanKind, errorHandler: ErrorHandler): Resource[G, Span[G]] =
        span.child(name, kind, errorHandler).mapK(fk).map(Span.mapK(fk))
    }

}
