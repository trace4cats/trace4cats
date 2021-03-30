package io.janstenpickle.trace4cats.avro.server

import java.net.InetSocketAddress

import cats.effect.{Concurrent, Resource, Sync}
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.io.tcp.{SocketGroup => TCPSocketGroup}
import fs2.io.udp.{SocketGroup => UDPSocketGroup}
import fs2.{Chunk, Pipe, Pull, Stream}
import org.typelevel.log4cats.Logger
import io.janstenpickle.trace4cats.avro.{agentPort, AvroInstances}
import io.janstenpickle.trace4cats.model.CompletedSpan
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory

object AvroServer {

  private def buffer[F[_]]: Pipe[F, Byte, Chunk[Byte]] =
    bytes => {
      def go(lastBytes: (Byte, Byte), state: List[Byte], stream: Stream[F, Byte]): Pull[F, Chunk[Byte], Unit] =
        stream.pull.uncons1.flatMap {
          case Some((hd, tl)) =>
            val newLastBytes = (lastBytes._2, hd)
            val newState = hd :: state
            if (newLastBytes == (0xc4.byteValue -> 0x02.byteValue))
              Pull.output1(Chunk(newState.drop(2).reverse: _*)) >> go((0, 0), List.empty, tl)
            else
              go(newLastBytes, newState, tl)

          case None => Pull.done
        }

      go((0, 0), List.empty, bytes).stream
    }

  private def decode[F[_]: Sync: Logger](schema: Schema)(bytes: Chunk[Byte]): F[Option[CompletedSpan]] =
    Sync[F]
      .delay {
        val reader = new GenericDatumReader[Any](schema)
        val decoder = DecoderFactory.get.binaryDecoder(bytes.toArray, null)
        val record = reader.read(null, decoder)

        record
      }
      .flatMap[Option[CompletedSpan]] { record =>
        Sync[F].fromEither(AvroInstances.completedSpanCodec.decode(record, schema).bimap(_.throwable, Some(_)))
      }
      .handleErrorWith(th => Logger[F].warn(th)("Failed to decode span batch").as(Option.empty[CompletedSpan]))

  def tcp[F[_]: Concurrent: ContextShift: Logger](sink: Pipe[F, CompletedSpan, Unit],
    port: Int = agentPort,
  ): Resource[F, Stream[F, Unit]] =
    for {
      avroSchema <- Resource.eval(AvroInstances.completedSpanSchema[F])
      socketGroup <- TCPSocketGroup(blocker)
      address <- Resource.eval(Sync[F].delay(new InetSocketAddress(port)))
    } yield socketGroup
      .server(address)
      .map { serverResource =>
        Stream.resource(serverResource).flatMap { server =>
          server
            .reads(8192)
            .through(buffer[F])
            .evalMap(decode[F](avroSchema))
            .unNone
            .through(sink)
        }
      }
      .parJoin(100)

  def udp[F[_]: Concurrent: ContextShift: Logger](sink: Pipe[F, CompletedSpan, Unit],
    port: Int = agentPort,
  ): Resource[F, Stream[F, Unit]] =
    for {
      avroSchema <- Resource.eval(AvroInstances.completedSpanSchema[F])
      address <- Resource.eval(Sync[F].delay(new InetSocketAddress(port)))
      socketGroup <- UDPSocketGroup[F](blocker)
      socket <- socketGroup.open(address)
    } yield socket
      .reads()
      .map(_.bytes)
      .evalMap(decode[F](avroSchema))
      .unNone
      .through(sink)
}
