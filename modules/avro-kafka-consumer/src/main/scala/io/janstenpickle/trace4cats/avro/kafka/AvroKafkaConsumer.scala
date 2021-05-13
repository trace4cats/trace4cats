package io.janstenpickle.trace4cats.avro.kafka

import cats.ApplicativeThrow
import cats.data.NonEmptyList
import cats.effect.kernel.{Async, Sync}
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.traverse._
import fs2.Stream
import fs2.kafka._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.avro.AvroInstances
import io.janstenpickle.trace4cats.model._
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory

object AvroKafkaConsumer {
  implicit def keyDeserializer[F[_]: Sync]: Deserializer[F, Option[TraceId]] =
    Deserializer.lift { bytes =>
      Sync[F].delay(Option(bytes).flatMap(TraceId(_)))
    }

  def valueDeserializer[F[_]: Sync: Logger](schema: Schema): Deserializer[F, Option[CompletedSpan]] =
    Deserializer.lift { ba =>
      Option(ba).flatTraverse { bytes =>
        Sync[F]
          .delay {
            val reader = new GenericDatumReader[Any](schema)
            val decoder = DecoderFactory.get.binaryDecoder(bytes, null)
            val record = reader.read(null, decoder)

            record
          }
          .flatMap[Option[CompletedSpan]] { record =>
            Sync[F].fromEither(AvroInstances.completedSpanCodec.decode(record, schema).bimap(_.throwable, Some(_)))
          }
          .handleErrorWith(th => Logger[F].warn(th)("Failed to decode span").as(Option.empty[CompletedSpan]))

      }
    }

  def apply[F[_]: Async](
    bootStrapServers: NonEmptyList[String],
    consumerGroup: String,
    topic: String,
    modifySettings: ConsumerSettings[F, Option[TraceId], Option[CompletedSpan]] => ConsumerSettings[F, Option[
      TraceId
    ], Option[CompletedSpan]] = (s: ConsumerSettings[F, Option[TraceId], Option[CompletedSpan]]) => s,
  ): Stream[F, CompletedSpan] =
    Stream.eval(Slf4jLogger.create[F]).flatMap { implicit logger =>
      Stream
        .fromEither[F](AvroInstances.completedSpanCodec.schema.leftMap(_.throwable))
        .flatMap { schema =>
          implicit val deser: Deserializer[F, Option[CompletedSpan]] = valueDeserializer[F](schema)

          KafkaConsumer.stream(
            modifySettings(
              ConsumerSettings[F, Option[TraceId], Option[CompletedSpan]]
                .withBootstrapServers(bootStrapServers.mkString_(","))
                .withGroupId(consumerGroup)
                .withAutoOffsetReset(AutoOffsetReset.Latest)
            )
          )
        }
        .flatMap(apply[F](_, topic))
    }

  def apply[F[_]: ApplicativeThrow](
    consumer: KafkaConsumer[F, Option[TraceId], Option[CompletedSpan]],
    topic: String,
  ): Stream[F, CompletedSpan] =
    Stream
      .eval(consumer.subscribeTo(topic))
      .flatMap(_ => consumer.stream)
      .chunks
      .flatMap { records =>
        Stream.eval(CommittableOffsetBatch.fromFoldable(records.map(_.offset)).commit) >> Stream
          .chunk(records.map(_.record.value))
      }
      .unNone

}
