package io.janstenpickle.trace4cats.avro

import cats.effect.{Concurrent, Resource}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import cats.effect.Temporal

object AvroSpanCompleter {
  def udp[F[_]: Concurrent: ContextShift: Temporal](process: TraceProcess,
    host: String = agentHostname,
    port: Int = agentPort,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.eval(Slf4jLogger.create[F])
      exporter <- AvroSpanExporter.udp[F, Chunk](blocker, host, port)
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer

  def tcp[F[_]: Concurrent: ContextShift: Temporal](process: TraceProcess,
    host: String = agentHostname,
    port: Int = agentPort,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] = {
    for {
      implicit0(logger: Logger[F]) <- Resource.eval(Slf4jLogger.create[F])
      exporter <- AvroSpanExporter.tcp[F, Chunk](blocker, host, port)
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer
  }
}
