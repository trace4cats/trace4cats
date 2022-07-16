/*
rule = v0_14
 */

package fix

import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.`export`.QueuedSpanExporter
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.model.AttributeValue.StringValue
import io.janstenpickle.trace4cats.attributes.HostAttributes
import io.janstenpickle.trace4cats.sampling.dynamic.HotSwapSpanSampler
import io.janstenpickle.trace4cats.`export`.HotswapSpanCompleter
import io.janstenpickle.trace4cats.`export`.HotswapSpanExporter
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.sampling.dynamic.config.SamplerConfig
import io.janstenpickle.trace4cats.log.LogSpanExporter
import io.janstenpickle.trace4cats.filtering.AttributeFilter
import io.janstenpickle.trace4cats.sampling.tail.TailSpanSampler
import io.janstenpickle.trace4cats.rate.sampling.RateTailSpanSampler
import io.janstenpickle.trace4cats.rate.sampling.RateSpanSampler
import io.janstenpickle.trace4cats.base.context.Ask
import io.janstenpickle.trace4cats.base.optics.Lens
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import io.janstenpickle.trace4cats.model.ArbitraryAttributeValues
import io.janstenpickle.trace4cats.meta.PipeTracer
import io.janstenpickle.trace4cats.http4s.server.Http4sResourceKleislis
import io.janstenpickle.trace4cats.http4s.server.syntax._
import io.janstenpickle.trace4cats.http4s.client.ClientTracer
import io.janstenpickle.trace4cats.http4s.client.syntax._
import io.janstenpickle.trace4cats.natchez.conversions.toNatchez._
import io.janstenpickle.trace4cats.natchez.conversions.fromNatchez._
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.avro.kafka.AvroKafkaConsumer
import io.janstenpickle.trace4cats.avro.kafka.AvroKafkaSpanExporter
import io.janstenpickle.trace4cats.jaeger.JaegerSpanExporter
import io.janstenpickle.trace4cats.opentelemetry.otlp.OpenTelemetryOtlpGrpcSpanExporter
import io.janstenpickle.trace4cats.opentelemetry.jaeger.OpenTelemetryJaegerSpanCompleter
import io.janstenpickle.trace4cats.stackdriver.StackdriverGrpcSpanCompleter
import io.janstenpickle.trace4cats.stackdriver.oauth.GoogleOAuth
import io.janstenpickle.trace4cats.stackdriver.project.ProjectIdProvider
import io.janstenpickle.trace4cats.datadog.DataDogSpanCompleter
import io.janstenpickle.trace4cats.newrelic.NewRelicSpanExporter
import io.janstenpickle.trace4cats.zipkin.ZipkinHttpSpanCompleter

object TraceTests {}
