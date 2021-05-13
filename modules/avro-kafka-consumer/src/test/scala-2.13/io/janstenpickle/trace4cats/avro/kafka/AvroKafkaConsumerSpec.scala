package io.janstenpickle.trace4cats.avro.kafka

import java.io.ByteArrayOutputStream

import cats.Eq
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.kafka.{AutoOffsetReset, ConsumerSettings}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.avro.AvroInstances
import io.janstenpickle.trace4cats.model.{CompletedSpan, TraceId}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.serialization.Serializer
import org.scalacheck.Shrink
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class AvroKafkaConsumerSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with EmbeddedKafka
    with ArbitraryInstances {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 3, maxDiscardedFactor = 50.0)

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = 0, zooKeeperPort = 0)

  val schema = AvroInstances.completedSpanCodec.schema.toOption.get

  implicit val keySerializer: Serializer[TraceId] = new Serializer[TraceId] {
    override def serialize(topic: String, data: TraceId): Array[Byte] = data.value
  }

  implicit val serializer: Serializer[CompletedSpan] = new Serializer[CompletedSpan] {
    override def serialize(topic: String, data: CompletedSpan): Array[Byte] = {
      val writer = new GenericDatumWriter[Any](schema)
      val out = new ByteArrayOutputStream

      val encoder = EncoderFactory.get.binaryEncoder(out, null)

      val record = AvroInstances.completedSpanCodec.encode(data).toOption.get

      writer.write(record, encoder)
      encoder.flush()
      val ba = out.toByteArray

      out.close()

      ba
    }
  }

  it should "read spans from kafka" in withRunningKafkaOnFoundPort(userDefinedConfig) { implicit actualConfig =>
    forAll { (span: CompletedSpan, topic: String, group: String) =>
      publishToKafka(topic, span.context.traceId, span)

      val ret = AvroKafkaConsumer[IO](
        NonEmptyList.one(s"localhost:${actualConfig.kafkaPort}"),
        group,
        topic,
        (s: ConsumerSettings[IO, Option[TraceId], Option[CompletedSpan]]) =>
          s.withAutoOffsetReset(AutoOffsetReset.Earliest)
      ).take(1).compile.toList.unsafeRunSync()

      assert(Eq.eqv(ret.head, span))

    }
  }
}
