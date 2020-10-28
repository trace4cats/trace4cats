package io.janstenpickle.trace4cats.avro.kafka

import io.janstenpickle.trace4cats.avro.AvroInstances._
import io.janstenpickle.trace4cats.model.{CompletedSpan, TraceProcess}
import vulcan.Codec
import vulcan.generic._

/**
  * Used when splitting up batches into separate kafka producer records
  */
case class KafkaSpan(process: TraceProcess, span: CompletedSpan)

object KafkaSpan {
  implicit val kafkaSpanCodec: Codec[KafkaSpan] = Codec.derive
}
