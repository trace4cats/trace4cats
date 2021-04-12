package io.janstenpickle.trace4cats.avro.kafka

import java.io.ByteArrayOutputStream

import cats.data.NonEmptyList
import cats.effect.kernel.{Async, Resource, Sync}
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.show._
import cats.{Functor, Traverse}
import fs2.kafka._
import io.janstenpickle.trace4cats.avro.AvroInstances
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceId}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.clients.producer.ProducerConfig

object AvroKafkaSpanExporter {
  implicit def keySerializer[F[_]: Sync]: Serializer[F, TraceId] = Serializer.string[F].contramap[TraceId](_.show)
  def valueSerializer[F[_]: Sync](schema: Schema): Serializer[F, CompletedSpan] =
    Serializer.lift[F, CompletedSpan] { span =>
      for {
        record <- AvroInstances.completedSpanCodec.encode(span).leftMap(_.throwable).liftTo[F]
        ba <-
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
      } yield ba
    }

  def apply[F[_]: Async, G[+_]: Traverse](
    bootStrapServers: NonEmptyList[String],
    topic: String,
    modifySettings: ProducerSettings[F, TraceId, CompletedSpan] => ProducerSettings[F, TraceId, CompletedSpan] =
      (x: ProducerSettings[F, TraceId, CompletedSpan]) => x
  ): Resource[F, SpanExporter[F, G]] =
    Resource
      .eval(AvroInstances.completedSpanCodec.schema.leftMap(_.throwable).map(valueSerializer[F]).liftTo[F])
      .flatMap { implicit ser =>
        KafkaProducer
          .resource(
            modifySettings(
              ProducerSettings[F, TraceId, CompletedSpan]
                .withBootstrapServers(bootStrapServers.mkString_(","))
                .withProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip")
            )
          )
          .map(fromProducer[F, G](_, topic))
      }

  def fromProducer[F[_]: Functor, G[+_]: Traverse](
    producer: KafkaProducer[F, TraceId, CompletedSpan],
    topic: String
  ): SpanExporter[F, G] =
    new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] =
        producer
          .produce(ProducerRecords[G, TraceId, CompletedSpan](batch.spans.map { span =>
            ProducerRecord(topic, span.context.traceId, span)
          }))
          .void
    }
}
