package io.janstenpickle.trace4cats

import cats.effect.kernel.Deferred
import cats.effect.testkit.{TestControl, TestInstances}
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, OutcomeIO}
import cats.implicits._
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration._

class SpanSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ArbitraryInstances
    with TestInstances {

  behavior.of("Span.root")

  it should "create a new ref span when not sampled" in forAll { (name: String, kind: SpanKind) =>
    assert(
      Span
        .root[IO](name, kind, SpanSampler.always, SpanCompleter.empty)
        .use(s => IO(s.isInstanceOf[RefSpan[IO]]))
        .unsafeRunSync()
    )
  }

  it should "create a new empty span when sampled" in forAll { (name: String, kind: SpanKind) =>
    assert(
      Span
        .root[IO](name, kind, SpanSampler.never, SpanCompleter.empty)
        .use(s => IO(s.isInstanceOf[EmptySpan[IO]]))
        .unsafeRunSync()
    )
  }

  it should "complete a new root span" in forAll { (name: String, kind: SpanKind, serviceName: String) =>
    val span = (for {
      completer <- RefSpanCompleter[IO](serviceName)
      _ <- Span.root[IO](name, kind, SpanSampler.always, completer).use(_ => IO.unit)
      spans <- completer.get
    } yield spans.head).unsafeRunSync()

    span.context.parent should be(None)
    span.context.isRemote should be(false)
    span.context.traceFlags.sampled should be(SampleDecision.Include)
  }

  it should "not complete a sampled root span" in forAll {
    (name: String, kind: SpanKind, status: SpanStatus, serviceName: String) =>
      val span = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <- Span.root[IO](name, kind, SpanSampler.never, completer).use(_.setStatus(status))
        spans <- completer.get
      } yield spans.headOption).unsafeRunSync()

      span should be(None)
  }

  behavior.of("Span.child")

  it should "create a new ref span from a parent context" in forAll {
    (name: String, parentContext: SpanContext, kind: SpanKind) =>
      assert(
        Span
          .child[IO](
            name,
            parentContext.copy(traceFlags = TraceFlags(sampled = SampleDecision.Include)),
            kind,
            SpanSampler.always,
            SpanCompleter.empty
          )
          .use(s => IO(s.isInstanceOf[RefSpan[IO]]))
          .unsafeRunSync()
      )
  }

  it should "create a new empty span from a sampled parent context" in forAll {
    (name: String, parentContext: SpanContext, kind: SpanKind) =>
      assert(
        Span
          .child[IO](
            name,
            parentContext.copy(traceFlags = TraceFlags(sampled = SampleDecision.Drop)),
            kind,
            SpanSampler.always,
            SpanCompleter.empty
          )
          .use(s => IO(s.isInstanceOf[EmptySpan[IO]]))
          .unsafeRunSync()
      )
  }

  it should "create a new span from a parent context" in forAll {
    (name: String, parentContext: SpanContext, kind: SpanKind, serviceName: String) =>
      val span = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <-
          Span
            .child[IO](
              name,
              parentContext.copy(traceFlags = TraceFlags(sampled = SampleDecision.Include)),
              kind,
              SpanSampler.always,
              completer
            )
            .use(_ => IO.unit)
        spans <- completer.get
      } yield spans.head).unsafeRunSync()

      span.name should be(name)
      span.kind should be(kind)
      span.context.parent should be(Some(Parent(parentContext.spanId, parentContext.isRemote)))
  }

  it should "not use the completer on a sampled span" in forAll {
    (name: String, parentContext: SpanContext, kind: SpanKind, status: SpanStatus, serviceName: String) =>
      val span = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <-
          Span
            .child[IO](
              name,
              parentContext.copy(traceFlags = TraceFlags(sampled = SampleDecision.Drop)),
              kind,
              SpanSampler.always,
              completer
            )
            .use(_.setStatus(status))
        spans <- completer.get
      } yield spans.headOption).unsafeRunSync()

      span should be(None)
  }

  behavior.of("RefSpan")

  it should "set the span name and kind" in forAll { (name: String, kind: SpanKind, serviceName: String) =>
    val span = (for {
      completer <- RefSpanCompleter[IO](serviceName)
      _ <- Span.root[IO](name, kind, SpanSampler.always, completer).use(_ => IO.unit)
      spans <- completer.get
    } yield spans.head).unsafeRunSync()

    span.name should be(name)
    span.kind should be(kind)
  }

  it should "create a child ref span" in forAll {
    (name: String, childName: String, kind: SpanKind, childKind: SpanKind, serviceName: String) =>
      val spans = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <-
          Span
            .root[IO](name, kind, SpanSampler.always, completer)
            .flatTap(_.child(childName, childKind))
            .use(_ => IO.unit)
        spans <- completer.get
      } yield spans).unsafeRunSync()

      spans.size should be(2)

      spans(1).context.parent should be(None)
      spans(1).context.isRemote should be(false)
      spans(1).context.traceFlags.sampled should be(SampleDecision.Include)
      spans(1).name should be(name)
      spans(1).kind should be(kind)

      spans.head.context.parent should be(Some(Parent(spans(1).context.spanId, isRemote = false)))
      spans.head.context.isRemote should be(false)
      spans.head.context.traceFlags.sampled should be(SampleDecision.Include)
      spans.head.name should be(childName)
      spans.head.kind should be(childKind)

  }

  it should "create a sampled child span" in forAll {
    (name: String, childName: String, kind: SpanKind, childKind: SpanKind, serviceName: String) =>
      val sampler = new SpanSampler[IO] {
        var callCount: Int = 0

        override def shouldSample(
          parentContext: Option[SpanContext],
          traceId: TraceId,
          spanName: String,
          spanKind: SpanKind
        ): IO[SampleDecision] =
          IO(
            if (callCount == 0) {
              callCount = callCount + 1
              SampleDecision.Include
            } else
              SampleDecision.Drop
          )
      }

      def assertSampled(span: Span[IO]) = assert(span.isInstanceOf[EmptySpan[IO]])

      val spans = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <-
          Span
            .root[IO](name, kind, sampler, completer)
            .flatTap(_.child(childName, childKind).map(assertSampled))
            .use(_ => IO.unit)
        spans <- completer.get
      } yield spans).unsafeRunSync()

      spans.size should be(1)

      spans.head.context.parent should be(None)
      spans.head.context.isRemote should be(false)
      spans.head.context.traceFlags.sampled should be(SampleDecision.Include)
      spans.head.name should be(name)
      spans.head.kind should be(kind)
  }

  it should "use the default status of OK when completed" in forAll {
    (name: String, kind: SpanKind, serviceName: String) =>
      val span = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <- Span.root[IO](name, kind, SpanSampler.always, completer).use(_ => IO.unit)
        spans <- completer.get
      } yield spans.head).unsafeRunSync()

      span.status should be(SpanStatus.Ok)
  }

  it should "use the provided status when completed" in forAll {
    (name: String, kind: SpanKind, status: SpanStatus, serviceName: String) =>
      val span = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <- Span.root[IO](name, kind, SpanSampler.always, completer).use(_.setStatus(status))
        spans <- completer.get
      } yield spans.head).unsafeRunSync()

      span.status should be(status)
  }

  it should "override the status to cancelled when execution is cancelled" in {
    forAll { (name: String, kind: SpanKind, status: SpanStatus, serviceName: String) =>
      val io = for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <- Deferred[IO, OutcomeIO[Unit]]
          .flatMap { stop =>
            val r = Span
              .root[IO](name, kind, SpanSampler.always, completer)
              .use(_.setStatus(status) >> IO.never: IO[Unit])
              .guaranteeCase(stop.complete(_).void)

            r.start.flatMap { fiber =>
              IO.sleep(200.millis) >> fiber.cancel >> stop.get
            }
          }
          .timeout(2.seconds)
        queue <- completer.get
        span = queue.head
      } yield span.status

      val result = TestControl.executeEmbed(io).unsafeRunSync()
      result shouldEqual SpanStatus.Cancelled
    }
  }

  it should "override the status to internal when execution fails" in forAll {
    (name: String, kind: SpanKind, status: SpanStatus, errorMsg: String, serviceName: String) =>
      val span = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <-
          Span
            .root[IO](name, kind, SpanSampler.always, completer)
            .use(_.setStatus(status) >> IO.raiseError[Unit](new RuntimeException(errorMsg)))
            .attempt
        spans <- completer.get
      } yield spans.head).unsafeRunSync()

      span.status should be(SpanStatus.Internal(errorMsg))
  }

  it should "override the status using the provided error handler when execution fails" in forAll {
    (
      name: String,
      kind: SpanKind,
      status: SpanStatus,
      overrideStatus: SpanStatus,
      errorMsg: String,
      serviceName: String
    ) =>
      val span = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <-
          Span
            .root[IO](
              name,
              kind,
              SpanSampler.always,
              completer,
              { case TestException(_) =>
                HandledError.Status(overrideStatus)
              }
            )
            .use(_.setStatus(status) >> IO.raiseError[Unit](TestException(errorMsg)))
            .attempt
        spans <- completer.get
      } yield spans.head).unsafeRunSync()

      span.status should be(overrideStatus)
  }

  it should "add a glob of attributes" in forAll {
    (name: String, kind: SpanKind, attributes: Map[String, AttributeValue], serviceName: String) =>
      val span = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <- Span.root[IO](name, kind, SpanSampler.always, completer).use(_.putAll(attributes.toList: _*))
        spans <- completer.get
      } yield spans.head).unsafeRunSync()

      (span.attributes should contain).theSameElementsAs(attributes)
  }

  it should "add individual attributes" in forAll {
    (name: String, kind: SpanKind, attributes: Map[String, AttributeValue], serviceName: String) =>
      val span = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <-
          Span
            .root[IO](name, kind, SpanSampler.always, completer)
            .use(span => attributes.toList.traverse { case (k, v) => span.put(k, v) })
        spans <- completer.get
      } yield spans.head).unsafeRunSync()

      (span.attributes should contain).theSameElementsAs(attributes)
  }

  behavior.of("EmptySpan")

  it should "never complete" in forAll { (name: String, kind: SpanKind, serviceName: String) =>
    val span = (for {
      completer <- RefSpanCompleter[IO](serviceName)
      _ <- Span.root[IO](name, kind, SpanSampler.never, completer).use(_ => IO.unit)
      spans <- completer.get
    } yield spans.headOption).unsafeRunSync()

    span should be(None)
  }

  it should "create a child empty span" in forAll {
    (name: String, childName: String, kind: SpanKind, childKind: SpanKind, serviceName: String) =>
      val spans = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        _ <-
          Span
            .root[IO](name, kind, SpanSampler.never, completer)
            .flatTap(_.child(childName, childKind))
            .use(_ => IO.unit)
        spans <- completer.get
      } yield spans.headOption).unsafeRunSync()

      spans.size should be(0)
  }

  case class TestException(message: String) extends RuntimeException
}
