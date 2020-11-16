package io.janstenpickle.trace4cats.collector.config

import io.circe.Decoder
import io.janstenpickle.trace4cats.collector.common.config._
import io.circe.generic.extras.semiauto._

case class CollectorConfig(
  otlpGrpc: List[OtlpGrpcConfig] = List.empty,
  jaegerProto: List[JaegerProtoConfig] = List.empty,
  stackdriverGrpc: List[StackdriverGrpcConfig] = List.empty
)
object CollectorConfig {
  implicit val decoder: Decoder[CollectorConfig] = deriveConfiguredDecoder
}

case class OtlpGrpcConfig(port: Int = 55680, host: String)
object OtlpGrpcConfig {
  implicit val decoder: Decoder[OtlpGrpcConfig] = deriveConfiguredDecoder
}

case class JaegerProtoConfig(port: Int = 14250, host: String)
object JaegerProtoConfig {
  implicit val decoder: Decoder[JaegerProtoConfig] = deriveConfiguredDecoder
}

case class StackdriverGrpcConfig(projectId: String)
object StackdriverGrpcConfig {
  implicit val decoder: Decoder[StackdriverGrpcConfig] = deriveConfiguredDecoder
}
