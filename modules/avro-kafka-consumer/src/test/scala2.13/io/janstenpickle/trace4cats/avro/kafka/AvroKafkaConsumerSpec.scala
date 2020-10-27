package io.janstenpickle.trace4cats.avro.kafka

import java.io.ByteArrayOutputStream

import cats.Eq
import cats.data.NonEmptyList
import cats.effect.{Blocker, IO}
import fs2.concurrent.Queue
import fs2.kafka.{AutoOffsetReset, ConsumerSettings}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.model.{Batch, TraceId}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.serialization.Serializer
import org.scalacheck.Shrink
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext

class AvroKafkaConsumerSpec
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

  implicit val keySerializer: Serializer[TraceId] = new Serializer[TraceId] {
    override def serialize(topic: String, data: TraceId): Array[Byte] = data.value
  }

  implicit val serializer: Serializer[KafkaSpan] = new Serializer[KafkaSpan] {
    override def serialize(topic: String, data: KafkaSpan): Array[Byte] = {
      val writer = new GenericDatumWriter[Any](schema)
      val out = new ByteArrayOutputStream

      val encoder = EncoderFactory.get.binaryEncoder(out, null)

      val record = KafkaSpan.kafkaSpanCodec.encode(data).toOption.get

      writer.write(record, encoder)
      encoder.flush()
      val ba = out.toByteArray

      out.close()

      ba
    }
  }

  it should "read spans from kafka" in withRunningKafkaOnFoundPort(userDefinedConfig) { implicit actualConfig =>
    forAll { (span: KafkaSpan, topic: String, group: String) =>
      publishToKafka(topic, span.span.context.traceId, span)

      val ret = (for {
        queue <- Queue.unbounded[IO, Batch]

        _ <- AvroKafkaConsumer[IO](
          blocker,
          NonEmptyList.one(s"localhost:${actualConfig.kafkaPort}"),
          group,
          topic,
          queue.enqueue,
          (s: ConsumerSettings[IO, Option[TraceId], Option[KafkaSpan]]) =>
            s.withAutoOffsetReset(AutoOffsetReset.Earliest)
        ).compile.drain.start
        ret <- queue.dequeue1
      } yield ret).unsafeRunSync()

      ret.spans.size should be(1)
      ret.process should be(span.process)
      assert(Eq.eqv(ret.spans.head, span.span))

    }
  }
}
