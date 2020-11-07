package io.janstenpickle.trace4cats.agent.kafka

import cats.data.NonEmptyList
import cats.effect.{Blocker, ExitCode, IO, Resource}
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import fs2.Chunk
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.QueuedSpanExporter
import io.janstenpickle.trace4cats.avro._
import io.janstenpickle.trace4cats.avro.kafka.AvroKafkaSpanExporter
import io.janstenpickle.trace4cats.avro.server.AvroServer

object AgentKafka
    extends CommandIOApp(name = "trace4cats-agent-kafka", header = "Trace 4 Cats Kafka Agent", version = "0.1.0") {

  val portOpt: Opts[Int] =
    Opts
      .env[Int](AgentPortEnv, help = "The port to run on.")
      .orElse(Opts.option[Int]("port", "The port to run on"))
      .withDefault(DefaultPort)

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

  val bufferSizeOpt: Opts[Int] =
    Opts
      .env[Int]("T4C_AGENT_BUFFER_SIZE", "Number of batches to buffer in case of network issues")
      .orElse(Opts.option[Int]("buffer-size", "Number of batches to buffer in case of network issues"))
      .withDefault(500)

  override def main: Opts[IO[ExitCode]] =
    (portOpt, kafkaBootstrapServersOpt, kafkaTopicOpt, bufferSizeOpt).mapN(run)

  def run(port: Int, kafkaBootstrapServers: NonEmptyList[String], kafkaTopic: String, bufferSize: Int): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]
      implicit0(logger: Logger[IO]) <- Resource.liftF(Slf4jLogger.create[IO])
      _ <- Resource.make(
        logger
          .info(s"Starting Trace 4 Cats Kafka Agent on udp://::$port. Forwarding to Kafka topic '$kafkaTopic'")
      )(_ => logger.info("Shutting down Trace 4 Cats Kafka Agent"))

      kafkaExporter <- AvroKafkaSpanExporter[IO, Chunk](blocker, kafkaBootstrapServers, kafkaTopic)

      queuedExporter <- QueuedSpanExporter(bufferSize, List("Avro Kafka" -> kafkaExporter))

      udpServer <- AvroServer.udp[IO](blocker, queuedExporter.pipe, port)
    } yield udpServer).use(_.compile.drain.as(ExitCode.Success))
}
