package io.janstenpickle.trace4cats.agent.kafka

import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, Resource}
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.agent.common.CommonAgent
import io.janstenpickle.trace4cats.agent.common.CommonAgent._
import io.janstenpickle.trace4cats.avro.kafka.AvroKafkaSpanExporter
import io.janstenpickle.trace4cats.kernel.BuildInfo
import io.janstenpickle.trace4cats.model.AttributeValue

object AgentKafka
    extends CommandIOApp(
      name = "trace4cats-agent-kafka",
      header = "Trace 4 Cats Kafka Agent",
      version = BuildInfo.version
    ) {

  val kafkaBootstrapServersHelp = "Kafka bootstrap servers"

  val kafkaBootstrapServersOpt: Opts[NonEmptyList[String]] =
    Opts
      .env[String]("T4C_AGENT_KAFKA_BOOTSTRAP_SERVERS", kafkaBootstrapServersHelp)
      .map { s =>
        NonEmptyList.fromListUnsafe(s.split(',').toList)
      }
      .orElse(Opts.options[String]("kafka-bootstrap-servers", kafkaBootstrapServersHelp))

  val kafkaTopicHelp = "Kafka topic"

  val kafkaTopicOpt: Opts[String] =
    Opts.env[String]("T4C_AGENT_KAFKA_TOPIC", kafkaTopicHelp).orElse(Opts.option[String]("kafka-topic", kafkaTopicHelp))

  override def main: Opts[IO[ExitCode]] =
    (portOpt, kafkaBootstrapServersOpt, kafkaTopicOpt, bufferSizeOpt, traceOpt, traceSampleOpt).mapN(run)

  def run(
    port: Int,
    kafkaBootstrapServers: NonEmptyList[String],
    kafkaTopic: String,
    bufferSize: Int,
    trace: Boolean,
    traceRate: Option[Double]
  ): IO[ExitCode] =
    Resource
      .eval(Slf4jLogger.create[IO])
      .flatMap { implicit logger: Logger[IO] =>
        AvroKafkaSpanExporter[IO, Chunk](kafkaBootstrapServers, kafkaTopic)
          .map(kafkaExporter =>
            CommonAgent.run[IO](
              port,
              bufferSize,
              "Avro Kafka",
              Map[String, AttributeValue](
                "bootstrap.servers" -> AttributeValue.StringList(kafkaBootstrapServers),
                "topic" -> kafkaTopic
              ),
              kafkaExporter,
              s"Kafka topic '$kafkaTopic'",
              trace,
              traceRate
            )
          )
      }
      .use(identity)
}
