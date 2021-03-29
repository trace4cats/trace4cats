package io.janstenpickle.trace4cats.avro

import java.io.ByteArrayOutputStream
import java.net.{ConnectException, InetSocketAddress}
import cats.effect.syntax.concurrent._
import cats.effect.{Concurrent, Resource, Sync}
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monad._
import cats.syntax.traverse._
import cats.{Applicative, Traverse}
import fs2.concurrent.Queue
import fs2.io.tcp.{Socket => TCPSocket, SocketGroup => TCPSocketGroup}
import fs2.io.udp.{Packet, Socket => UDPSocket, SocketGroup => UDPSocketGroup}
import fs2.{Chunk, Stream}
import org.typelevel.log4cats.Logger
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory

import scala.concurrent.duration._
import cats.effect.Temporal
import cats.effect.std.Semaphore

object AvroSpanExporter {
  //TODO: use one from cats-core when it's merged https://github.com/typelevel/cats/pull/3705
  private def replicateA_[F[_]: Applicative, A](fa: F[A])(n: Int): F[Unit] =
    (1 to n).map(_ => fa).foldLeft(Applicative[F].unit)(_ <* _)

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

  /** Creates a UDP exporter with an internal queue that accepts batches from the traced app.
    *
    * @param numFibers the capacity of the internal queue and the number of concurrent
    *                  workers that consume the queue and send batches via UDP; use numbers
    *                  greater than 1 at your own risk
    */
  def udp[F[_]: Concurrent: ContextShift: Temporal, G[_]: Traverse](host: String = agentHostname,
    port: Int = agentPort,
    numFibers: Int = 1
  ): Resource[F, SpanExporter[F, G]] = {
    val queueCapacity = numFibers
    val maxPermits = numFibers.toLong

    def write(
      schema: Schema,
      address: InetSocketAddress,
      semaphore: Semaphore[F],
      queue: Queue[F, Batch[G]],
      socket: UDPSocket[F]
    ): F[Unit] =
      Stream
        .repeatEval {
          for {
            batch <- queue.dequeue1
            _ <- semaphore.withPermit {
              batch.spans.traverse { span =>
                for {
                  ba <- encode[F](schema)(span)
                  _ <- socket.write(Packet(address, Chunk.bytes(ba)))
                } yield ()
              }
            }
          } yield ()
        }
        .compile
        .drain

    for {
      avroSchema <- Resource.eval(AvroInstances.completedSpanSchema[F])
      address <- Resource.eval(Sync[F].delay(new InetSocketAddress(host, port)))
      queue <- Resource.eval(Queue.bounded[F, Batch[G]](queueCapacity))
      semaphore <- Resource.eval(Semaphore[F](maxPermits))
      socketGroup <- UDPSocketGroup(blocker)
      socket <- socketGroup.open()
      writer = Resource.make(
        Stream
          .retry(write(avroSchema, address, semaphore, queue, socket), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .start
      )(fiber =>
        Temporal[F].sleep(50.millis).whileM_(semaphore.available.map(_ < maxPermits)) >>
          fiber.cancel
      )
      _ <- replicateA_(writer)(numFibers)
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = queue.enqueue1(batch)
    }
  }

  /** Creates a TCP exporter with an internal queue that accepts batches from the traced app.
    *
    * @param numFibers the capacity of the internal queue and the number of concurrent
    *                  workers that consume the queue and send batches via TCP; use numbers
    *                  greater than 1 at your own risk
    */
  def tcp[F[_]: Concurrent: ContextShift: Temporal: Logger, G[_]: Traverse](host: String = agentHostname,
    port: Int = agentPort,
    numFibers: Int = 1
  ): Resource[F, SpanExporter[F, G]] = {
    val queueCapacity = numFibers
    val maxPermits = numFibers.toLong

    def connect(socketGroup: TCPSocketGroup, address: InetSocketAddress): Stream[F, TCPSocket[F]] =
      Stream
        .resource(socketGroup.client(address))
        .recoverWith { case _: ConnectException =>
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
              for {
                batch <- queue.dequeue1
                _ <- semaphore.withPermit {
                  batch.spans.traverse { span =>
                    for {
                      ba <- encode[F](schema)(span)
                      withTerminator = ba ++ Array(0xc4.byteValue, 0x02.byteValue)
                      _ <- socket.write(Chunk.bytes(withTerminator))
                    } yield ()
                  }
                }
              } yield ()
            }
        }
        .compile
        .drain

    for {
      avroSchema <- Resource.eval(AvroInstances.completedSpanSchema[F])
      address <- Resource.eval(Sync[F].delay(new InetSocketAddress(host, port)))
      queue <- Resource.eval(Queue.bounded[F, Batch[G]](queueCapacity))
      semaphore <- Resource.eval(Semaphore[F](maxPermits))
      socketGroup <- TCPSocketGroup(blocker)
      writer = Resource.make(
        Stream
          .retry(write(avroSchema, address, semaphore, queue, socketGroup), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .start
      )(fiber =>
        Temporal[F].sleep(50.millis).whileM_(semaphore.available.map(_ < maxPermits)) >>
          fiber.cancel
      )
      _ <- replicateA_(writer)(numFibers)
    } yield new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = queue.enqueue1(batch)
    }
  }
}
