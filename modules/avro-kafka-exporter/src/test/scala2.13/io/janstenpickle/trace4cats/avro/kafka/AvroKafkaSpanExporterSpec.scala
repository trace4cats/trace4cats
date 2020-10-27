package io.janstenpickle.trace4cats.avro.kafka

import cats.Eq
import cats.data.NonEmptyList
import cats.effect.{Blocker, IO}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.common.serialization.Deserializer
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

  val blocker = Blocker.liftExecutionContext(ExecutionContext.global)

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 3, maxDiscardedFactor = 50.0)

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = 0, zooKeeperPort = 0)

  val schema = KafkaSpan.kafkaSpanCodec.schema.toOption.get

  implicit val deserializer: Deserializer[KafkaSpan] = new Deserializer[KafkaSpan] {
    override def deserialize(topic: String, data: Array[Byte]): KafkaSpan = {
      val reader = new GenericDatumReader[Any](schema)
      val decoder = DecoderFactory.get.binaryDecoder(data, null)
      val record = reader.read(null, decoder)

      KafkaSpan.kafkaSpanCodec.decode(record, schema).toOption.get
    }
  }

  it should "serialize a batch of spans to kafka" in withRunningKafkaOnFoundPort(userDefinedConfig) {
    implicit actualConfig =>
      forAll { (batch: Batch) =>
        AvroKafkaSpanExporter[IO](blocker, NonEmptyList.one(s"localhost:${actualConfig.kafkaPort}"), "test")
          .use { exporter =>
            exporter.exportBatch(batch)
          }
          .unsafeRunSync()

        val messages = consumeNumberMessagesFrom[KafkaSpan]("test", batch.spans.size)
        val res = messages.groupBy(_.process)

        res.size should be(1)
        res.head._1 should be(batch.process)
        assert(Eq.eqv(res.head._2.map(_.span), batch.spans))
      }

  }
}
