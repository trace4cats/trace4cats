package io.janstenpickle.trace4cats.jaeger

import cats.effect.kernel.{Async, Resource}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess

import scala.concurrent.ExecutionContext
import scala.util.Try

object JaegerSpanCompleter {
  def apply[F[_]: Async](
    process: TraceProcess,
    host: String = Option(System.getenv("JAEGER_AGENT_HOST")).getOrElse(UdpSender.DEFAULT_AGENT_UDP_HOST),
    port: Int = Option(System.getenv("JAEGER_AGENT_PORT"))
      .flatMap(p => Try(p.toInt).toOption)
      .getOrElse(UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT),
    config: CompleterConfig = CompleterConfig(),
    blocker: Option[ExecutionContext] = None
  ): Resource[F, SpanCompleter[F]] =
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      JaegerSpanExporter[F, Chunk](Some(process), host, port, blocker)
        .flatMap(QueuedSpanCompleter[F](process, _, config))
    }
}
