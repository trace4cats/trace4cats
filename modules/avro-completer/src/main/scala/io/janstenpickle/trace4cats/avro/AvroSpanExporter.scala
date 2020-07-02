package io.janstenpickle.trace4cats.avro

import java.io.ByteArrayOutputStream
import java.net.{ConnectException, InetSocketAddress}

import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.syntax.either._
import cats.syntax.flatMap._
import fs2.concurrent.Queue
import fs2.io.tcp.{Socket => TCPSocket, SocketGroup => TCPSocketGroup}
import fs2.io.udp.{Packet, Socket => UDPSocket, SocketGroup => UDPSocketGroup}
import fs2.{Chunk, Stream}
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory

import scala.concurrent.duration._

object AvroSpanExporter {
  private def encode[F[_]: Sync](schema: Schema)(batch: Batch): F[Array[Byte]] =
    Sync[F]
      .fromEither(AvroInstances.batchCodec.encode(batch).leftMap(_.throwable))
      .flatMap { record =>
        Resource
          .make(
            Sync[F]
              .delay {
                val writer = new GenericDatumWriter[Any](schema)
                val out = new ByteArrayOutputStream

                val encoder = EncoderFactory.get.binaryEncoder(out, null)

                (writer, out, encoder)
              }
          ) {
            case (_, out, _) =>
              Sync[F].delay(out.close())
          }
          .use {
            case (writer, out, encoder) =>
              Sync[F].delay {
                writer.write(record, encoder)
                encoder.flush()
                out.toByteArray
              }
          }
      }

  def udp[F[_]: Concurrent: ContextShift: Timer](
    blocker: Blocker,
    host: String = agentHostname,
    port: Int = agentPort,
    bufferSize: Int = 2000,
  ): Resource[F, SpanExporter[F]] = {
    def write(schema: Schema, address: InetSocketAddress, batchQueue: Queue[F, Batch], socket: UDPSocket[F]): F[Unit] =
      batchQueue.dequeue
        .evalMap(encode[F](schema))
        .map { ba =>
          Packet(address, Chunk.bytes(ba))
        }
        .through(socket.writes())
        .compile
        .drain

    for {
      avroSchema <- Resource.liftF(AvroInstances.batchSchema[F])
      address <- Resource.liftF(Sync[F].delay(new InetSocketAddress(host, port)))
      batchQueue <- Resource.liftF(Queue.circularBuffer[F, Batch](bufferSize))
      socketGroup <- UDPSocketGroup(blocker)
      socket <- socketGroup.open()
      _ <- Stream
        .retry(write(avroSchema, address, batchQueue, socket), 5.seconds, _ + 1.second, Int.MaxValue)
        .compile
        .drain
        .background
    } yield
      new SpanExporter[F] {
        override def exportBatch(batch: Batch): F[Unit] = batchQueue.enqueue1(batch)
      }
  }

  def tcp[F[_]: Concurrent: ContextShift: Timer](
    blocker: Blocker,
    host: String = agentHostname,
    port: Int = agentPort,
    bufferSize: Int = 2000,
  ): Resource[F, SpanExporter[F]] = {
    def connect(socketGroup: TCPSocketGroup, address: InetSocketAddress): Stream[F, TCPSocket[F]] =
      Stream
        .resource(socketGroup.client(address))
        .handleErrorWith {
          case _: ConnectException => connect(socketGroup, address).delayBy(5.seconds)
        }

    def write(
      schema: Schema,
      address: InetSocketAddress,
      batchQueue: Queue[F, Batch],
      socketGroup: TCPSocketGroup
    ): F[Unit] =
      connect(socketGroup, address)
        .flatMap { socket =>
          batchQueue.dequeue
            .evalMap(encode[F](schema))
            .flatMap { ba =>
              val withTerminator = ba ++ Array(0xC4.byteValue, 0x02.byteValue)
              Stream.chunk(Chunk.bytes(withTerminator))
            }
            .through(socket.writes())
        }
        .compile
        .drain

    for {
      avroSchema <- Resource.liftF(AvroInstances.batchSchema[F])
      address <- Resource.liftF(Sync[F].delay(new InetSocketAddress(host, port)))
      batchQueue <- Resource.liftF(Queue.circularBuffer[F, Batch](bufferSize))
      socketGroup <- TCPSocketGroup(blocker)
      _ <- Stream
        .retry(write(avroSchema, address, batchQueue, socketGroup), 5.seconds, _ + 1.second, Int.MaxValue)
        .compile
        .drain
        .background
    } yield
      new SpanExporter[F] {
        override def exportBatch(batch: Batch): F[Unit] = batchQueue.enqueue1(batch)
      }
  }
}
