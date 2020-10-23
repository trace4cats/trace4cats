package io.janstenpickle.trace4cats.avro.kafka

import cats.Id
import cats.data.NonEmptyList
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, Sync, Timer}
import cats.kernel.Semigroup
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.semigroup._
import fs2.{Pipe, Stream}
import fs2.kafka._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.model.{AttributeValue, Batch, CompletedSpan, TraceProcess}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory

import scala.concurrent.duration._

object AvroKafkaConsumer {
  def valueDeserializer[F[_]: Sync: Logger](schema: Schema): Deserializer[F, Option[KafkaSpan]] =
    Deserializer.instance { (_, _, bytes) =>
      Sync[F]
        .delay {
          val reader = new GenericDatumReader[Any](schema)
          val decoder = DecoderFactory.get.binaryDecoder(bytes, null)
          val record = reader.read(null, decoder)

          record
        }
        .flatMap[Option[KafkaSpan]] { record =>
          Sync[F].fromEither(KafkaSpan.kafkaSpanCodec.decode(record, schema).bimap(_.throwable, Some(_)))
        }
        .handleErrorWith(th => Logger[F].warn(th)("Failed to decode span").as(Option.empty[KafkaSpan]))

    }

  case class BatchConfig(size: Int, timeoutSeconds: Int)

  def apply[F[_]: ConcurrentEffect: ContextShift: Timer](
    blocker: Blocker,
    bootStrapServers: NonEmptyList[String],
    consumerGroup: String,
    topic: String,
    sink: Pipe[F, Batch, Unit],
    modifySettings: ConsumerSettings[F, String, Option[KafkaSpan]] => ConsumerSettings[F, String, Option[KafkaSpan]] =
      (s: ConsumerSettings[F, String, Option[KafkaSpan]]) => s,
    batch: Option[BatchConfig] = None
  ): Stream[F, Unit] = Stream.eval(Slf4jLogger.create[F]).flatMap { implicit logger =>
    Stream
      .fromEither[F](KafkaSpan.kafkaSpanCodec.schema.leftMap(_.throwable))
      .flatMap { schema =>
        implicit val deser: Deserializer[F, Option[KafkaSpan]] = valueDeserializer[F](schema)

        consumerStream(
          modifySettings(
            ConsumerSettings[F, String, Option[KafkaSpan]]
              .withBlocker(blocker)
              .withBootstrapServers(bootStrapServers.mkString_(","))
              .withGroupId(consumerGroup)
              .withAutoOffsetReset(AutoOffsetReset.Latest)
          )
        )
      }
      .flatMap(apply[F](_, topic, sink, batch))
  }

  private implicit val attrMapSemigroup: Semigroup[Map[String, AttributeValue]] =
    new Semigroup[Map[String, AttributeValue]] {
      override def combine(
        x: Map[String, AttributeValue],
        y: Map[String, AttributeValue]
      ): Map[String, AttributeValue] = x ++ y
    }

  def apply[F[_]: Concurrent: Timer](
    consumer: KafkaConsumer[F, String, Option[KafkaSpan]],
    topic: String,
    sink: Pipe[F, Batch, Unit],
    batch: Option[BatchConfig]
  ): Stream[F, Unit] = {
    val stream = Stream.eval(consumer.subscribe[Id](topic)).flatMap(_ => consumer.stream)

    batch.fold(stream.chunks)(config => stream.groupWithin(config.size, config.timeoutSeconds.seconds)).evalMap {
      records =>
        lazy val committableBatch = CommittableOffsetBatch.fromFoldable(records.map(_.offset))

        lazy val (data, attrs) =
          records.foldLeft((Map.empty[String, List[CompletedSpan]], Map.empty[String, Map[String, AttributeValue]])) {
            case (acc @ (spans, attributes), record) =>
              record.record.value.fold(acc) { span =>
                (
                  spans |+| Map(span.process.serviceName -> List(span.span)),
                  attributes |+| Map(span.process.serviceName -> span.process.attributes)
                )
              }
          }

        Stream
          .fromIterator(data.iterator)
          .map { case (sn, ss) => Batch(TraceProcess(sn, attrs.getOrElse(sn, Map.empty)), ss) }
          .through(sink)
          .compile
          .drain >> committableBatch.commit
    }
  }
}
