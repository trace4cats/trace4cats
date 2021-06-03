package io.janstenpickle.trace4cats.example

import cats.Parallel
import cats.effect.kernel.{Async, Resource}
import cats.instances.list._
import cats.syntax.foldable._
import cats.syntax.parallel._
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.log.LogSpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import org.typelevel.log4cats.Logger

/** This example shows how many different completers may be combined into a single completer using
  * the provided monoid instance.
  *
  * Note that both `Parallel` and `Applicative` instances of the monoid are available, if you don't
  * provide a `Parallel` typeclass then completers will be executed in sequence
  */
object AllCompleters {
  def apply[F[_]: Async: Parallel: Logger](process: TraceProcess): Resource[F, SpanCompleter[F]] =
    List(
      AvroSpanCompleter.udp[F](process),
      // TODO add these back once new artifacts are published
//      JaegerSpanCompleter[F](process),
//      OpenTelemetryJaegerSpanCompleter[F](process),
//      OpenTelemetryOtlpGrpcSpanCompleter[F](process),
//      OpenTelemetryOtlpHttpSpanCompleter.blazeClient[F](process),
//      StackdriverGrpcSpanCompleter[F](process, "gcp-project-id-123"),
//      StackdriverHttpSpanCompleter.blazeClient[F](process)
    ).parSequence.map { completers =>
      (LogSpanCompleter[F](process) :: completers).combineAll
    }

}
