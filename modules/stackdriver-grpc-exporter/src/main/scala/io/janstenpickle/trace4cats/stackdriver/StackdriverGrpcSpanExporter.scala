package io.janstenpickle.trace4cats.stackdriver

import java.time.Instant
import cats.Foldable
import cats.data.NonEmptyList
import cats.effect.kernel.{Async, Resource, Sync}
import cats.effect.syntax.async._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.show._
import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.trace.v2.{TraceServiceClient, TraceServiceSettings}
import com.google.devtools.cloudtrace.v2.Span.Attributes
import com.google.devtools.cloudtrace.v2.{AttributeValue => GAttributeValue, TruncatableString => GTruncatableString, _}
import com.google.protobuf.{BoolValue, Timestamp}
import com.google.rpc.Status
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.stackdriver.common.StackdriverConstants._
import io.janstenpickle.trace4cats.stackdriver.common.TruncatableString

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object StackdriverGrpcSpanExporter {
  def apply[F[_]: Async, G[_]: Foldable](
    projectId: String,
    credentials: Option[Credentials] = None,
    requestTimeout: FiniteDuration = 5.seconds,
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] = {
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

    def toTruncatableStringProto(string: String) = {
      val truncatableString = TruncatableString(string)
      GTruncatableString.newBuilder
        .setValue(truncatableString.value)
        .setTruncatedByteCount(truncatableString.truncatedByteCount)
        .build
    }

    def toTimestampProto(timestamp: Instant): Timestamp =
      Timestamp.newBuilder.setSeconds(timestamp.getEpochSecond).setNanos(timestamp.getNano).build

    def toDisplayName(spanName: String, spanKind: SpanKind) =
      spanKind match {
        case SpanKind.Server if !spanName.startsWith(ServerPrefix) => ServerPrefix + spanName
        case SpanKind.Client if !spanName.startsWith(ClientPrefix) => ClientPrefix + spanName
        case SpanKind.Consumer if !spanName.startsWith(ServerPrefix) => ServerPrefix + spanName
        case SpanKind.Producer if !spanName.startsWith(ClientPrefix) => ClientPrefix + spanName
        case _ => spanName
      }

    def toAttributesProto(attributes: Map[String, AttributeValue]): Attributes =
      attributes.toList
        .foldLeft(Attributes.newBuilder()) { case (acc, (k, v)) =>
          acc.putAttributeMap(
            k,
            (v match {
              case AttributeValue.StringValue(value) =>
                GAttributeValue.newBuilder().setStringValue(toTruncatableStringProto(value.value))
              case AttributeValue.BooleanValue(value) => GAttributeValue.newBuilder().setBoolValue(value.value)
              case AttributeValue.DoubleValue(value) =>
                GAttributeValue.newBuilder().setStringValue(toTruncatableStringProto(value.value.show))
              case AttributeValue.LongValue(value) => GAttributeValue.newBuilder().setIntValue(value.value)
              case vs: AttributeValue.AttributeList =>
                GAttributeValue.newBuilder().setStringValue(toTruncatableStringProto(vs.show))
            }).build()
          )

        }
        .build()

    def toStatusProto(status: SpanStatus) =
      Status
        .newBuilder()
        .setCode(status.canonicalCode)
        .build()

    def toLinksProto(links: Option[NonEmptyList[Link]]): Span.Links =
      links
        .fold(Span.Links.newBuilder())(_.foldLeft(Span.Links.newBuilder()) { (builder, link) =>
          builder.addLink(
            Span.Link
              .newBuilder()
              .setType(Span.Link.Type.PARENT_LINKED_SPAN)
              .setTraceId(link.traceId.show)
              .setSpanId(link.spanId.show)
          )
        })
        .build()

    def convert(completedSpan: CompletedSpan): Span = {
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
          .setAttributes(toAttributesProto(completedSpan.allAttributes))
          .setStatus(toStatusProto(completedSpan.status))
          .setLinks(toLinksProto(completedSpan.links))

      val builder = completedSpan.context.parent.fold(spanBuilder) { parent =>
        spanBuilder.setParentSpanId(parent.spanId.show).setSameProcessAsParentSpan(BoolValue.of(!parent.isRemote))
      }

      builder.build()
    }

    def liftApiFuture[A](ffa: F[ApiFuture[A]]): F[A] = {
      for {
        fut <- ffa
        ec <- Async[F].executionContext
        a <- Async[F].async_[A] { cb =>
          ApiFutures.addCallback(
            fut,
            new ApiFutureCallback[A] {
              def onFailure(t: Throwable): Unit = cb(Left(t))
              def onSuccess(result: A): Unit = cb(Right(result))
            },
            ec.execute _
          )
        }
      } yield a
    }

    def write(client: TraceServiceClient, spans: G[CompletedSpan]): F[Unit] =
      for {
        request <- Sync[F].delay(
          BatchWriteSpansRequest
            .newBuilder()
            .setName(projectName.toString)
            .addAllSpans(
              spans
                .foldLeft(scala.collection.mutable.ListBuffer.empty[Span]) { (buf, span) =>
                  buf += convert(span)
                }
                .asJava
            )
            .build()
        )
        call = liftApiFuture(Sync[F].delay(client.batchWriteSpansCallable().futureCall(request)))
        _ <- ec.fold(call)(call.evalOn)
      } yield ()

    Resource.make(traceClient)(client => Sync[F].delay(client.shutdown())).map { client =>
      new SpanExporter[F, G] {
        override def exportBatch(batch: Batch[G]): F[Unit] = write(client, batch.spans)
      }
    }
  }
}
