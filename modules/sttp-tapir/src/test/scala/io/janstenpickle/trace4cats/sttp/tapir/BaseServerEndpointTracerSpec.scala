package io.janstenpickle.trace4cats.sttp.tapir

import cats.data.NonEmptyList
import cats.effect.{Concurrent, ContextShift, Resource, Timer}
import cats.syntax.all._
import cats.{~>, Id}
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{CompletedSpan, SpanKind, SpanStatus}
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.syntax.kleisli._
import org.http4s.{Headers, Uri}
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s._

import scala.collection.immutable.Queue

abstract class BaseServerEndpointTracerSpec[F[_]: Concurrent: ContextShift: Timer](
  unsafeRunK: F ~> Id,
  injectEndpoints: EntryPoint[F] => List[ServerEndpoint[_, _, _, Any, F]],
  checkMkContextErrors: Boolean
) extends AnyFlatSpec
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with Http4sDsl[F] {

  private val authHeader = Headers("X-Auth-Token" -> "aa89bba323bf4c7b929264b0c177e2c5")

  it should "record a span when the response is OK" in {
    evaluateTrace(NonEmptyList.one("devices/1"), authHeader) { spans =>
      spans.size should be(1)
      spans.head.name should be("GET /devices/{deviceId}")
      spans.head.kind should be(SpanKind.Server)
      spans.head.status should be(SpanStatus.Ok)
    }
  }

  it should "correctly set span status when the server logic returns a error" in {
    evaluateTrace(NonEmptyList.one("devices/0"), authHeader) { spans =>
      spans.size should be(1)
      spans.head.name should be("GET /devices/{deviceId}")
      spans.head.kind should be(SpanKind.Server)
      spans.head.status should be(SpanStatus.NotFound)
    }
  }

  it should "correctly set span status when the server logic throws an exception" in {
    evaluateTrace(NonEmptyList.one("vendors/0"), authHeader) { spans =>
      spans.size should be(1)
      spans.head.name should be("GET /vendors/{vendorId}")
      spans.head.kind should be(SpanKind.Server)
      spans.head.status should be(SpanStatus.NotFound)
    }
  }

  if (checkMkContextErrors) {
    it should "correctly set span status when context creation returns a error" in {
      evaluateTrace(NonEmptyList.one("devices/100")) { spans =>
        spans.size should be(1)
        spans.head.name should be("GET /devices/{deviceId}")
        spans.head.kind should be(SpanKind.Server)
        spans.head.status should be(SpanStatus.Unauthenticated)
      }
    }

    it should "correctly set span status when context creation throws an exception" in {
      evaluateTrace(NonEmptyList.one("vendors/11")) { spans =>
        spans.size should be(1)
        spans.head.name should be("GET /vendors/{vendorId}")
        spans.head.kind should be(SpanKind.Server)
        spans.head.status should be(SpanStatus.Unauthenticated)
      }
    }
  }

  def evaluateTrace(paths: NonEmptyList[String] = NonEmptyList.one("/"), headers: Headers = Headers.empty)(
    fa: Queue[CompletedSpan] => Assertion
  ): Assertion = {
    val clientDsl = new Http4sClientDsl[F] {}
    import clientDsl._

    unsafeRunK.apply(
      (for {
        completer <- Resource.eval(RefSpanCompleter[F]("test"))
        ep = EntryPoint[F](SpanSampler.always[F], completer)
        serverEndpoints = injectEndpoints(ep)
        app = Http4sServerInterpreter[F]().toRoutes(serverEndpoints).orNotFound
      } yield (app, completer))
        .use { case (app, completer) =>
          for {
            _ <- paths.traverse(path => app.run(GET(Uri.unsafeFromString(s"/$path")).withHeaders(headers)).attempt)
            spans <- completer.get
          } yield fa(spans)
        }
    )
  }

}
