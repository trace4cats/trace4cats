package io.janstenpickle.trace4cats.fs2.syntax

import cats.Applicative
import cats.data.Kleisli
import cats.effect.IO
import fs2.Stream
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{CompletedSpan, SampleDecision, SpanContext, TraceFlags, TraceHeaders}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import cats.syntax.show._
import cats.effect.Temporal

class Fs2StreamSyntaxSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ArbitraryInstances
    with Fs2StreamSyntax {
  type IOTrace[A] = Kleisli[IO, Span[IO], A]

  implicit val ioTimer: Temporal[IO] = IO.timer(ExecutionContext.global)

  def hasParent(spans: Queue[CompletedSpan], span: String, parent: String): Assertion =
    spans.find(_.name == span).flatMap(_.context.parent.map(_.spanId.show)) should be(
      spans.find(_.name == parent).map(_.context.spanId.show)
    )

  it should "trace an effect" in forAll { (spanName: String, serviceName: String, element: String) =>
    val rootName = s"root-$element"

    val res = (for {
      completer <- RefSpanCompleter[IO](serviceName)
      ep = EntryPoint[IO](SpanSampler.always[IO], completer)
      _ <- Stream
        .emit[IO, String](rootName)
        .inject(ep, str => str)
        .evalMap(spanName)(_ => IO.unit)
        .endTrace
        .compile
        .drain

      spans <- completer.get
    } yield spans).unsafeRunSync()

    res.size should be(2)
    res.map(_.name) should contain theSameElementsAs List(spanName, rootName)
    hasParent(res, spanName, rootName)

  }

  it should "trace multiple effects" in forAll { (name1: String, name2: String, serviceName: String, element: String) =>
    val rootName = s"root-$element"
    val spanName1 = s"span1-$name1"
    val spanName2 = s"span2-$name2"

    val res = (for {
      completer <- RefSpanCompleter[IO](serviceName)
      ep = EntryPoint[IO](SpanSampler.always[IO], completer)
      _ <- Stream
        .emit[IO, String](rootName)
        .inject(ep, str => str)
        .evalMap(spanName1)(_ => IO.unit)
        .evalMap(spanName2)(_ => IO.unit)
        .endTrace
        .compile
        .drain

      spans <- completer.get
    } yield spans).unsafeRunSync()

    res.size should be(3)
    res.map(_.name) should contain theSameElementsAs List(spanName1, spanName2, rootName)
    hasParent(res, spanName1, rootName)
    hasParent(res, spanName2, rootName)
  }

  it should "evaluate a traced effect" in forAll { (spanName: String, serviceName: String, element: String) =>
    val rootName = s"root-$element"

    val res = (for {
      completer <- RefSpanCompleter[IO](serviceName)
      ep = EntryPoint[IO](SpanSampler.always[IO], completer)
      _ <- Stream
        .emit[IO, String](rootName)
        .inject(ep, str => str)
        .evalMapTrace(_ => Trace[IOTrace].span(spanName)(Applicative[IOTrace].unit))
        .endTrace
        .compile
        .drain

      spans <- completer.get
    } yield spans).unsafeRunSync()

    res.size should be(2)
    res.map(_.name) should contain theSameElementsAs List(spanName, rootName)
    hasParent(res, spanName, rootName)
  }

  it should "evaluate multiple traced effects" in forAll {
    (name1: String, name2: String, serviceName: String, element: String) =>
      val rootName = s"root-$element"
      val spanName1 = s"span1-$name1"
      val spanName2 = s"span2-$name2"

      val res = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        ep = EntryPoint[IO](SpanSampler.always[IO], completer)
        _ <- Stream
          .emit[IO, String](rootName)
          .inject(ep, str => str)
          .evalMapTrace(_ => Trace[IOTrace].span(spanName1)(Applicative[IOTrace].unit))
          .evalMapTrace(_ => Trace[IOTrace].span(spanName2)(Applicative[IOTrace].unit))
          .endTrace
          .compile
          .drain

        spans <- completer.get
      } yield spans).unsafeRunSync()

      res.size should be(3)
      res.map(_.name) should contain theSameElementsAs List(spanName1, spanName2, rootName)
      hasParent(res, spanName1, rootName)
      hasParent(res, spanName2, rootName)
  }

  it should "lift the stream effect type to the trace type and evaluate a traced effect" in forAll {
    (spanName: String, serviceName: String, element: String) =>
      val rootName = s"root-$element"

      val res = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        ep = EntryPoint[IO](SpanSampler.always[IO], completer)
        _ <- Stream
          .emit[IO, String](rootName)
          .inject(ep, str => str)
          .liftTrace[IOTrace]
          .evalMap(_ => Trace[IOTrace].span(spanName)(Applicative[IOTrace].unit))
          .endTrace[IO]
          .compile
          .drain

        spans <- completer.get
      } yield spans).unsafeRunSync()

      res.size should be(2)
      res.map(_.name) should contain theSameElementsAs List(spanName, rootName)
      hasParent(res, spanName, rootName)
  }

  it should "lift the stream effect type to the trace type and evaluate multiple traced effects" in forAll {
    (name1: String, name2: String, serviceName: String, element: String) =>
      val rootName = s"root-$element"
      val spanName1 = s"span1-$name1"
      val spanName2 = s"span2-$name2"

      val res = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        ep = EntryPoint[IO](SpanSampler.always[IO], completer)
        _ <- Stream
          .emit[IO, String](rootName)
          .inject(ep, str => str)
          .liftTrace[IOTrace]
          .evalMap(_ => Trace[IOTrace].span(spanName1)(Applicative[IOTrace].unit))
          .evalMap(_ => Trace[IOTrace].span(spanName2)(Applicative[IOTrace].unit))
          .endTrace[IO]
          .compile
          .drain

        spans <- completer.get
      } yield spans).unsafeRunSync()

      res.size should be(3)
      res.map(_.name) should contain theSameElementsAs List(spanName1, spanName2, rootName)
      hasParent(res, spanName1, rootName)
      hasParent(res, spanName2, rootName)
  }

  it should "continue from a remote parent when sample flag is set to include" in forAll {
    (spanName: String, serviceName: String, element: String, parent: SpanContext) =>
      val rootName = s"root-$element"

      val parentHeaders = ToHeaders.all.fromContext(parent.copy(traceFlags = TraceFlags(SampleDecision.Include)))

      val res = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        ep = EntryPoint[IO](SpanSampler.always[IO], completer)
        _ <- Stream
          .emit[IO, (String, TraceHeaders)](rootName -> parentHeaders)
          .injectContinue(ep, _._1)(_._2)
          .evalMap(spanName)(_ => IO.unit)
          .endTrace
          .compile
          .drain

        spans <- completer.get
      } yield spans).unsafeRunSync()

      res.size should be(2)
      res.map(_.name) should contain theSameElementsAs List(spanName, rootName)
      hasParent(res, spanName, rootName)
      res.find(_.name == rootName).flatMap(_.context.parent.map(_.spanId.show)) should be(Some(parent.spanId.show))

  }

  it should "continue from a remote parent when sample flag is set to drop" in forAll {
    (spanName: String, serviceName: String, element: String, parent: SpanContext) =>
      val rootName = s"root-$element"

      val parentHeaders = ToHeaders.all.fromContext(parent.copy(traceFlags = TraceFlags(SampleDecision.Drop)))

      val res = (for {
        completer <- RefSpanCompleter[IO](serviceName)
        ep = EntryPoint[IO](SpanSampler.always[IO], completer)
        _ <- Stream
          .emit[IO, (String, TraceHeaders)](rootName -> parentHeaders)
          .injectContinue(ep, _._1)(_._2)
          .evalMap(spanName)(_ => IO.unit)
          .endTrace
          .compile
          .drain

        spans <- completer.get
      } yield spans).unsafeRunSync()

      res.size should be(0)
  }
}
