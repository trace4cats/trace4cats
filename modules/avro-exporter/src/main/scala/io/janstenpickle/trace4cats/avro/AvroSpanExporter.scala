package io.janstenpickle.trace4cats.avro

import java.io.ByteArrayOutputStream
import java.net.{ConnectException, InetSocketAddress}

import cats.Applicative
import cats.effect.concurrent.{MVar, Semaphore}
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monad._
import fs2.io.tcp.{Socket => TCPSocket, SocketGroup => TCPSocketGroup}
import fs2.io.udp.{Packet, Socket => UDPSocket, SocketGroup => UDPSocketGroup}
import fs2.{Chunk, Stream}
import io.chrisdavenport.log4cats.Logger
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

  def udp[F[_]: Concurrent: ContextShift: Timer: Logger](
    blocker: Blocker,
    host: String = agentHostname,
    port: Int = agentPort,
  ): Resource[F, SpanExporter[F]] = {

    def write(
      schema: Schema,
      address: InetSocketAddress,
      semaphore: Semaphore[F],
      mvar: MVar[F, Batch],
      socket: UDPSocket[F]
    ): F[Unit] =
      Stream
        .repeatEval {
          (for {
            batch <- mvar.take
            _ <- semaphore.acquire
            ba <- encode[F](schema)(batch)
            _ <- socket.write(Packet(address, Chunk.bytes(ba)))
          } yield ()).guarantee(semaphore.release)
        }
        .compile
        .drain

    for {
      avroSchema <- Resource.liftF(AvroInstances.batchSchema[F])
      address <- Resource.liftF(Sync[F].delay(new InetSocketAddress(host, port)))
      mvar <- Resource.liftF(MVar.empty[F, Batch])
      semaphore <- Resource.liftF(Semaphore[F](Long.MaxValue))
      socketGroup <- UDPSocketGroup(blocker)
      socket <- socketGroup.open()
      _ <- Resource.make(
        Stream
          .retry(write(avroSchema, address, semaphore, mvar, socket), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .start
      )(
        fiber =>
          Applicative[F].unit.whileM_(for {
            mvarSet <- mvar.isEmpty.map(!_)
            semaphoreSet <- semaphore.count.map(_ == 0)
            _ <- Timer[F].sleep(50.millis)
          } yield mvarSet || semaphoreSet) >> fiber.cancel
      )
    } yield
      new SpanExporter[F] {
        override def exportBatch(batch: Batch): F[Unit] = mvar.put(batch)
      }
  }

  def tcp[F[_]: Concurrent: ContextShift: Timer: Logger](
    blocker: Blocker,
    host: String = agentHostname,
    port: Int = agentPort,
  ): Resource[F, SpanExporter[F]] = {
    def connect(socketGroup: TCPSocketGroup, address: InetSocketAddress): Stream[F, TCPSocket[F]] =
      Stream
        .resource(socketGroup.client(address))
        .handleErrorWith {
          case _: ConnectException =>
            Stream.eval(Logger[F].warn(s"Failed to connect to tcp://$host:$port, retrying in 5s")) >> connect(
              socketGroup,
              address
            ).delayBy(5.seconds)
        }

    def write(
      schema: Schema,
      address: InetSocketAddress,
      semaphore: Semaphore[F],
      mvar: MVar[F, Batch],
      socketGroup: TCPSocketGroup
    ): F[Unit] =
      connect(socketGroup, address)
        .flatMap { socket =>
          Stream
            .repeatEval {
              (for {
                batch <- mvar.take
                _ <- semaphore.acquire
                ba <- encode[F](schema)(batch)
                withTerminator = ba ++ Array(0xC4.byteValue, 0x02.byteValue)
                _ <- socket.write(Chunk.bytes(withTerminator))
              } yield ()).guarantee(semaphore.release)
            }
        }
        .compile
        .drain

    for {
      avroSchema <- Resource.liftF(AvroInstances.batchSchema[F])
      address <- Resource.liftF(Sync[F].delay(new InetSocketAddress(host, port)))
      mvar <- Resource.liftF(MVar.empty[F, Batch])
      semaphore <- Resource.liftF(Semaphore[F](Long.MaxValue))
      socketGroup <- TCPSocketGroup(blocker)
      _ <- Resource.make(
        Stream
          .retry(write(avroSchema, address, semaphore, mvar, socketGroup), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .start
      )(
        fiber =>
          Applicative[F].unit.whileM_(for {
            mvarSet <- mvar.isEmpty.map(!_)
            semaphoreSet <- semaphore.count.map(_ == 0)
            _ <- Timer[F].sleep(50.millis)
          } yield mvarSet || semaphoreSet) >> fiber.cancel
      )
    } yield
      new SpanExporter[F] {
        override def exportBatch(batch: Batch): F[Unit] = mvar.put(batch)
      }
  }
}
