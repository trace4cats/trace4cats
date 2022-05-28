package trace4cats.model

import cats.{Applicative, Monad}
import cats.data.Chain
import cats.syntax.foldable._
import cats.syntax.functor._

case class TraceProcessBuilder[F[_]] private (
  serviceName: String,
  private val lazyAttributes: Chain[F[Map[String, AttributeValue]]]
) {
  def withAttribute(key: String, value: AttributeValue)(implicit F: Applicative[F]): TraceProcessBuilder[F] =
    withAttributes(Map(key -> value))

  def withAttributes(attrs: (String, AttributeValue)*)(implicit F: Applicative[F]): TraceProcessBuilder[F] =
    withAttributes(attrs.toMap)

  def withAttributes(attrs: Map[String, AttributeValue])(implicit F: Applicative[F]): TraceProcessBuilder[F] =
    this.copy(serviceName, lazyAttributes.append(F.pure(attrs)))

  def withAttributes(attrs: F[Map[String, AttributeValue]]): TraceProcessBuilder[F] =
    this.copy(serviceName, lazyAttributes.append(attrs))

  def build(implicit F: Monad[F]): F[TraceProcess] =
    lazyAttributes
      .foldLeftM(Map.empty[String, AttributeValue]) { case (acc, attrs) => attrs.map(acc ++ _) }
      .map(attrs => TraceProcess(serviceName, attrs))
}

object TraceProcessBuilder {
  def apply[F[_]](serviceName: String): TraceProcessBuilder[F] = new TraceProcessBuilder[F](serviceName, Chain.empty)
}
