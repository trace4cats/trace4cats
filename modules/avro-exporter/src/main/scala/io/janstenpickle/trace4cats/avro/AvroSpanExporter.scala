package io.janstenpickle.trace4cats.avro

import java.io.ByteArrayOutputStream
import java.net.{ConnectException, InetSocketAddress}

import cats.effect.concurrent.Semaphore
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monad._
import cats.syntax.traverse._
import cats.{Applicative, Traverse}
import fs2.concurrent.{InspectableQueue, Queue}
import fs2.io.tcp.{Socket => TCPSocket, SocketGroup => TCPSocketGroup}
import fs2.io.udp.{Packet, Socket => UDPSocket, SocketGroup => UDPSocketGroup}
import fs2.{Chunk, Stream}
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory

import scala.concurrent.duration._

object AvroSpanExporter {
  private def encode[F[_]: Sync](schema: Schema)(span: CompletedSpan): F[Array[Byte]] =
    Sync[F]
      .fromEither(AvroInstances.completedSpanCodec.encode(span).leftMap(_.throwable))
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
          ) { case (_, out, _) =>
            Sync[F].delay(out.close())
          }
          .use { case (writer, out, encoder) =>
            Sync[F].delay {
              writer.write(record, encoder)
              encoder.flush()
              out.toByteArray
            }
          }
      }

  def udp[F[_]: Concurrent: ContextShift: Timer: Logger, G[_]: Traverse](
    blocker: Blocker,
    host: String = agentHostname,
    port: Int = agentPort,
  ): Resource[F, SpanExporter[F, G]] = {

    def write(
      schema: Schema,
      address: InetSocketAddress,
      semaphore: Semaphore[F],
      queue: Queue[F, Batch[G]],
      socket: UDPSocket[F]
    ): F[Unit] =
      Stream
        .repeatEval {
          (for {
            batch <- queue.dequeue1
            _ <- semaphore.acquire
            _ <- batch.spans.traverse { span =>
              for {
                ba <- encode[F](schema)(span)
                _ <- socket.write(Packet(address, Chunk.bytes(ba)))
              } yield ()
            }
          } yield ()).guarantee(semaphore.release)
        }
        .compile
        .drain

    for {
      avroSchema <- Resource.liftF(AvroInstances.completedSpanSchema[F])
      address <- Resource.liftF(Sync[F].delay(new InetSocketAddress(host, port)))
      queue <- Resource.liftF(InspectableQueue.bounded[F, Batch[G]](1))
      semaphore <- Resource.liftF(Semaphore[F](Long.MaxValue))
      socketGroup <- UDPSocketGroup(blocker)
      socket <- socketGroup.open()
      _ <- Resource.make(
        Stream
          .retry(write(avroSchema, address, semaphore, queue, socket), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .start
      )(fiber =>
        Applicative[F].unit.whileM_(for {
          queueNonEmpty <- queue.getSize.map(_ != 0)
          semaphoreSet <- semaphore.count.map(_ == 0)
          _ <- Timer[F].sleep(50.millis)
        } yield queueNonEmpty || semaphoreSet) >> fiber.cancel
      )
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = queue.enqueue1(batch)
    }
  }

  def tcp[F[_]: Concurrent: ContextShift: Timer: Logger, G[_]: Traverse](
    blocker: Blocker,
    host: String = agentHostname,
    port: Int = agentPort,
  ): Resource[F, SpanExporter[F, G]] = {
    def connect(socketGroup: TCPSocketGroup, address: InetSocketAddress): Stream[F, TCPSocket[F]] =
      Stream
        .resource(socketGroup.client(address))
        .handleErrorWith { case _: ConnectException =>
          Stream.eval(Logger[F].warn(s"Failed to connect to tcp://$host:$port, retrying in 5s")) >> connect(
            socketGroup,
            address
          ).delayBy(5.seconds)
        }

    def write(
      schema: Schema,
      address: InetSocketAddress,
      semaphore: Semaphore[F],
      queue: Queue[F, Batch[G]],
      socketGroup: TCPSocketGroup
    ): F[Unit] =
      connect(socketGroup, address)
        .flatMap { socket =>
          Stream
            .repeatEval {
              (for {
                batch <- queue.dequeue1
                _ <- semaphore.acquire
                _ <- batch.spans.traverse { span =>
                  for {
                    ba <- encode[F](schema)(span)
                    withTerminator = ba ++ Array(0xc4.byteValue, 0x02.byteValue)
                    _ <- socket.write(Chunk.bytes(withTerminator))
                  } yield ()
                }
              } yield ()).guarantee(semaphore.release)
            }
        }
        .compile
        .drain

    for {
      avroSchema <- Resource.liftF(AvroInstances.completedSpanSchema[F])
      address <- Resource.liftF(Sync[F].delay(new InetSocketAddress(host, port)))
      queue <- Resource.liftF(InspectableQueue.bounded[F, Batch[G]](1))
      semaphore <- Resource.liftF(Semaphore[F](Long.MaxValue))
      socketGroup <- TCPSocketGroup(blocker)
      _ <- Resource.make(
        Stream
          .retry(write(avroSchema, address, semaphore, queue, socketGroup), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .start
      )(fiber =>
        Timer[F]
          .sleep(50.millis)
          .whileM_(for {
            queueNonEmpty <- queue.getSize.map(_ != 0)
            semaphoreSet <- semaphore.count.map(_ == 0)
          } yield queueNonEmpty || semaphoreSet) >> fiber.cancel
      )
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = queue.enqueue1(batch)
    }
  }
}
