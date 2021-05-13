package io.janstenpickle.trace4cats.avro.kafka

import cats.Eq
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import io.janstenpickle.trace4cats.avro.AvroInstances
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.serialization.Deserializer
import org.scalacheck.Shrink
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class AvroKafkaSpanExporterSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with EmbeddedKafka
    with ArbitraryInstances {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 3, maxDiscardedFactor = 50.0)

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = 0, zooKeeperPort = 0)

  val schema = AvroInstances.completedSpanCodec.schema.toOption.get

  implicit val deserializer: Deserializer[CompletedSpan] = new Deserializer[CompletedSpan] {
    override def deserialize(topic: String, data: Array[Byte]): CompletedSpan = {
      val reader = new GenericDatumReader[Any](schema)
      val decoder = DecoderFactory.get.binaryDecoder(data, null)
      val record = reader.read(null, decoder)

      AvroInstances.completedSpanCodec.decode(record, schema).toOption.get
    }
  }

  it should "serialize a batch of spans to kafka" in withRunningKafkaOnFoundPort(userDefinedConfig) {
    implicit actualConfig =>
      forAll { (batch: Batch[Chunk]) =>
        val res = AvroKafkaSpanExporter[IO, Chunk](NonEmptyList.one(s"localhost:${actualConfig.kafkaPort}"), "test")
          .use { exporter =>
            exporter.exportBatch(batch) >>
              IO(consumeNumberMessagesFrom[CompletedSpan]("test", batch.spans.size))
          }
          .unsafeRunSync()

        res.size should be(batch.spans.size)
        assert(Eq.eqv(res, batch.spans.toList))
      }
  }
}
