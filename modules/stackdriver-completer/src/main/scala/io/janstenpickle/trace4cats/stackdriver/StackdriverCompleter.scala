package io.janstenpickle.trace4cats.stackdriver

import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.syntax.functor._
import cats.syntax.show._
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.trace.v2.{TraceServiceClient, TraceServiceSettings}
import com.google.devtools.cloudtrace.v2.Span.Attributes
import com.google.devtools.cloudtrace.v2._
import com.google.protobuf.{BoolValue, Timestamp}
import com.google.rpc.Status
import fs2.Stream
import fs2.concurrent.Queue
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model._

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object StackdriverCompleter {
  final val ServiceNameAttributeKey = "service-name"
  private final val ServerPrefix = "Recv."
  private final val ClientPrefix = "Sent."

  def apply[F[_]: Concurrent: ContextShift: Timer](
    blocker: Blocker,
    process: TraceProcess,
    projectId: String,
    credentials: Option[Credentials] = None,
    requestTimeout: FiniteDuration = 5.seconds,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] = {
    val projectName = ProjectName.of(projectId)

    val traceClient: F[TraceServiceClient] = Sync[F].delay {
      val creds = credentials.getOrElse(GoogleCredentials.getApplicationDefault())

      val clientBuilder = TraceServiceSettings.newBuilder
        .setCredentialsProvider(FixedCredentialsProvider.create(creds))

      clientBuilder
        .batchWriteSpansSettings()
        .setSimpleTimeoutNoRetries(org.threeten.bp.Duration.ofMillis(requestTimeout.toMillis))

      TraceServiceClient.create(clientBuilder.build())
    }

    def toTruncatableStringProto(string: String) =
      TruncatableString.newBuilder.setValue(string).setTruncatedByteCount(0).build

    def toTimestampProto(timestamp: Long): Timestamp = {
      val instant = Instant.ofEpochMilli(TimeUnit.MICROSECONDS.toMillis(timestamp))
      Timestamp.newBuilder.setSeconds(instant.getEpochSecond).setNanos(instant.getNano).build
    }

    def toDisplayName(spanName: String, spanKind: SpanKind) = spanKind match {
      case SpanKind.Server if !spanName.startsWith(ServerPrefix) => ServerPrefix + spanName
      case SpanKind.Client if !spanName.startsWith(ClientPrefix) => ClientPrefix + spanName
      case SpanKind.Consumer if !spanName.startsWith(ServerPrefix) => ServerPrefix + spanName
      case SpanKind.Producer if !spanName.startsWith(ClientPrefix) => ClientPrefix + spanName
      case _ => spanName
    }

    def toAttributesProto(process: TraceProcess, attributes: Map[String, TraceValue]): Attributes =
      (process.attributes.updated(ServiceNameAttributeKey, process.serviceName) ++ attributes).toList
        .foldLeft(Attributes.newBuilder()) {
          case (acc, (k, v)) =>
            acc.putAttributeMap(
              k,
              (v match {
                case TraceValue.StringValue(value) =>
                  AttributeValue.newBuilder().setStringValue(toTruncatableStringProto(value))
                case TraceValue.BooleanValue(value) => AttributeValue.newBuilder().setBoolValue(value)
                case TraceValue.DoubleValue(value) => AttributeValue.newBuilder().setIntValue(value.toLong)
              }).build()
            )

        }
        .build()

    def toStatusProto(status: SpanStatus) =
      Status
        .newBuilder()
        .setCode(status match {
          case SpanStatus.Ok => 1
          case SpanStatus.Cancelled => 2
          case SpanStatus.Internal => 13
        })
        .build()

    def convert(process: TraceProcess, completedSpan: CompletedSpan): Span = {
      val spanIdHex = completedSpan.context.spanId.show

      val spanName =
        SpanName.newBuilder.setProject(projectId).setTrace(completedSpan.context.traceId.show).setSpan(spanIdHex).build

      val spanBuilder =
        Span
          .newBuilder()
          .setName(spanName.toString)
          .setSpanId(spanIdHex)
          .setDisplayName(toTruncatableStringProto(toDisplayName(completedSpan.name, completedSpan.kind)))
          .setStartTime(toTimestampProto(completedSpan.start))
          .setEndTime(toTimestampProto(completedSpan.end))
          .setAttributes(toAttributesProto(process, completedSpan.attributes))
          .setStatus(toStatusProto(completedSpan.status))

      val builder = completedSpan.context.parent.fold(spanBuilder) { parent =>
        spanBuilder.setParentSpanId(parent.spanId.show).setSameProcessAsParentSpan(BoolValue.of(!parent.isRemote))
      }

      builder.build()
    }

    def write(client: TraceServiceClient, process: TraceProcess, spans: List[CompletedSpan]) =
      blocker.delay(client.batchWriteSpans(projectName, spans.map(convert(process, _)).asJava))

    for {
      client <- Resource.liftF(traceClient)
      queue <- Resource.liftF(Queue.circularBuffer[F, CompletedSpan](bufferSize))
      _ <- Stream
        .retry(
          queue.dequeue
            .groupWithin(batchSize, batchTimeout)
            .evalMap { spans =>
              write(client, process, spans.toList)
            }
            .compile
            .drain,
          5.seconds,
          _ + 1.second,
          Int.MaxValue
        )
        .compile
        .drain
        .background
    } yield
      new SpanCompleter[F] {
        override def complete(span: CompletedSpan): F[Unit] = queue.enqueue1(span)
        override def completeBatch(batch: Batch): F[Unit] = write(client, batch.process, batch.spans).void
      }
  }
}
