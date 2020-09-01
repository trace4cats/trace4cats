package io.janstenpickle.trace4cats.http4s

import java.net.ServerSocket
import java.util.concurrent.Executors

import cats.ApplicativeError
import cats.data.{Kleisli, NonEmptyList}
import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.http4s.common.Http4sStatusMapping
import io.janstenpickle.trace4cats.http4s.server.HttpSyntax
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{CompletedSpan, SpanKind, SpanStatus}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.all._
import org.http4s.{Challenge, HttpApp, HttpRoutes, Response}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext

class HttpSyntaxSpec
    extends AnyFlatSpec
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with HttpSyntax
    with Http4sDsl[Kleisli[IO, Span[IO], *]] {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))
  implicit val responseArb: Arbitrary[TraceIO[Response[TraceIO]]] =
    Arbitrary(
      Gen.oneOf(
        List(
          Ok(),
          BadRequest(),
          Unauthorized.apply(`WWW-Authenticate`.apply(NonEmptyList.one(Challenge("", "", Map.empty)))),
          Forbidden(),
          TooManyRequests(),
          BadGateway(),
          ServiceUnavailable(),
          GatewayTimeout()
        )
      )
    )

  type TraceIO[A] = Kleisli[IO, Span[IO], A]

  it should "record a span when the response is OK" in {
    val app = HttpRoutes.of[TraceIO] {
      case GET -> Root => Ok()
    }

    evaluateTrace(app, app.orNotFound) { spans =>
      spans.size should be(1)
      spans.head.name should be("GET /")
      spans.head.kind should be(SpanKind.Server)
      spans.head.status should be(SpanStatus.Ok)
    }
  }

  it should "correctly set span status when the server throws an exception" in forAll { errorMsg: String =>
    val app = HttpRoutes.of[TraceIO] {
      case GET -> Root => ApplicativeError[TraceIO, Throwable].raiseError(new RuntimeException(errorMsg))
    }

    evaluateTrace(app, app.orNotFound) { spans =>
      spans.size should be(1)
      spans.head.name should be("GET /")
      spans.head.kind should be(SpanKind.Server)
      spans.head.status should be(SpanStatus.Internal(errorMsg))
    }
  }

  it should "correctly set the span status from the http response" in forAll { response: TraceIO[Response[TraceIO]] =>
    val app = HttpRoutes.of[TraceIO] {
      case GET -> Root => response
    }

    val expectedStatus = Http4sStatusMapping.toSpanStatus(Span.noop[IO].use(response.run).unsafeRunSync().status)

    evaluateTrace(app, app.orNotFound) { spans =>
      spans.size should be(1)
      spans.head.name should be("GET /")
      spans.head.kind should be(SpanKind.Server)
      spans.head.status should be(expectedStatus)
    }
  }

  def evaluateTrace(routes: HttpRoutes[TraceIO], app: HttpApp[TraceIO])(
    fa: Queue[CompletedSpan] => Assertion
  ): Assertion = {
    val s = new ServerSocket(0)
    val port = s.getLocalPort

    def test(f: EntryPoint[IO] => HttpApp[IO]): Assertion =
      (for {
        blocker <- Blocker[IO]
        completer <- Resource.liftF(RefSpanCompleter[IO])
        ep = EntryPoint[IO](SpanSampler.always, completer)
        _ <- BlazeServerBuilder[IO](blocker.blockingContext)
          .bindHttp(port, "localhost")
          .withHttpApp(f(ep))
          .resource
        client <- BlazeClientBuilder[IO](blocker.blockingContext).resource
      } yield (client, completer))
        .use {
          case (client, completer) =>
            for {
              _ <- client.expect[String](s"http://localhost:$port").attempt
              spans <- completer.get
            } yield fa(spans)
        }
        .unsafeRunSync()

    test(routes.inject(_).orNotFound)
    test(app.inject(_))
  }

}
