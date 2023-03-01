package trace4cats.kernel.headers

import cats.syntax.either._
import cats.syntax.option._
import cats.syntax.show._
import org.apache.commons.codec.binary.Hex
import org.typelevel.ci._
import trace4cats.kernel.ToHeaders
import trace4cats.model._

private[trace4cats] class GoogleCloudTraceToHeaders extends ToHeaders {
  import GoogleCloudTraceToHeaders._

  override def toContext(headers: TraceHeaders): Option[SpanContext] =
    headers.values.get(headerName).flatMap(parse(_).toOption)

  override def fromContext(context: SpanContext): TraceHeaders = {
    val enabled = if (context.traceFlags.sampled.toBoolean) "1" else "0"
    TraceHeaders.ofCi(headerName -> show"${context.traceId}/${spanIdAsBigInt(context.spanId).toString};o=$enabled")
  }
}

private[trace4cats] object GoogleCloudTraceToHeaders {

  val headerName = ci"x-cloud-trace-context"

  // header format is "X-Cloud-Trace-Context: TRACE_ID/SPAN_ID;o=TRACE_TRUE"
  // from https://cloud.google.com/trace/docs/setup
  val headerPattern =
    """(?xi)
      |([^\/]+) # trace ID
      |\/
      |([^;]+)       # span ID (unsigned decimal)
      |(?:
      |;
      |o=(.*)     # trace enabled flag
      |)?
      |""".stripMargin.r

  def parse(header: String): Either[Throwable, SpanContext] = header match {
    case headerPattern(traceId, spanId, enabled0) =>
      val enabled = Option(enabled0)
      for {
        traceId <- Either.fromOption(TraceId.fromHexString(traceId), new Exception("invalid trace ID"))
        spanId <- Either.fromOption(
          SpanId.fromHexString("%016x".format(BigInt(spanId))),
          new Exception("invalid span ID")
        )
      } yield SpanContext(
        traceId = traceId,
        spanId = spanId,
        parent = none,
        traceFlags = TraceFlags(if (enabled == Some("1")) SampleDecision.Include else SampleDecision.Drop),
        traceState = TraceState.empty,
        isRemote = true
      )

    case _ =>
      Left(new Exception("invalid header (format should be: `TRACE_ID/SPAN_ID;o=TRACE_TRUE`)"))
  }

  def spanIdAsBigInt(spanId: SpanId): BigInt =
    BigInt(Hex.encodeHexString(spanId.value), 16)
}
