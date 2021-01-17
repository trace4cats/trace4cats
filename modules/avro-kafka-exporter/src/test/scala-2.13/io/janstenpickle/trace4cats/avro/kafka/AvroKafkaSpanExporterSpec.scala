package io.janstenpickle.trace4cats.avro.kafka

import cats.Eq
import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.show._
import fs2.Chunk
import fs2.kafka.{AutoOffsetReset, ConsumerSettings, Deserializer, KafkaConsumer}
import io.janstenpickle.trace4cats.avro.AvroInstances
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceId}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory
import org.scalacheck.Shrink
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext

class AvroKafkaSpanExporterSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with EmbeddedKafka
    with ArbitraryInstances {
  implicit val contextShift = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 3, maxDiscardedFactor = 50.0)

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = 0, zooKeeperPort = 0)

  val schema = AvroInstances.completedSpanCodec.schema.toOption.get

  val keyDeserializer: Deserializer[IO, TraceId] = Deserializer
    .string[IO]
    .map(TraceId.fromHexString(_).get)
    .suspend

  val valueDeserializer: Deserializer[IO, CompletedSpan] = Deserializer.lift { data =>
    val reader = new GenericDatumReader[Any](schema)
    val decoder = DecoderFactory.get.binaryDecoder(data, null)
    val record = reader.read(null, decoder)

    IO(AvroInstances.completedSpanCodec.decode(record, schema).toOption.get)
  }

  it should "serialize a batch of spans to kafka" in withRunningKafkaOnFoundPort(userDefinedConfig) {
    implicit actualConfig =>
      val bootStrapServers = NonEmptyList.one(s"localhost:${actualConfig.kafkaPort}")
      val topic = "test"

      forAll { (batch: Batch[Chunk]) =>
        val resources = for {
          exporter <- AvroKafkaSpanExporter[IO, Chunk](bootStrapServers, topic)
          consumer <- KafkaConsumer
            .resource[IO]
            .using(
              ConsumerSettings[IO, TraceId, CompletedSpan](keyDeserializer, valueDeserializer)
                .withBootstrapServers(bootStrapServers.mkString_(","))
                .withGroupId("embedded-kafka-spec")
                .withAutoOffsetReset(AutoOffsetReset.Earliest)
                .withEnableAutoCommit(true)
            )
            .evalTap(_.subscribeTo(topic))
        } yield (exporter, consumer)

        val res =
          resources
            .use { case (exporter, consumer) =>
              exporter.exportBatch(batch) >>
                consumer.stream.take(batch.spans.size.toLong).map(_.record.value).compile.toList
            }
            .unsafeRunSync()

        res.size should be(batch.spans.size)
        assert(Eq.eqv(res.sortBy(_.context.spanId.show), batch.spans.toList.sortBy(_.context.spanId.show)))
      }
  }
}
