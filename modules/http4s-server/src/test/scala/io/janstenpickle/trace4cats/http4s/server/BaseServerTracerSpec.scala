package io.janstenpickle.trace4cats.http4s.server

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.{Resource, Sync, Timer}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{~>, ApplicativeThrow, Id}
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.http4s.common.{Http4sRequestFilter, Http4sStatusMapping}
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{CompletedSpan, SpanKind, SpanStatus}
import org.http4s._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.all._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.immutable.Queue

abstract class BaseServerTracerSpec[F[_]: Sync: Timer, G[_]: Sync](
  unsafeRunK: F ~> Id,
  noopProvideK: G ~> F,
  injectRoutes: (HttpRoutes[G], Http4sRequestFilter, EntryPoint[F]) => HttpRoutes[F],
  injectApp: (HttpApp[G], Http4sRequestFilter, EntryPoint[F]) => HttpApp[F]
) extends AnyFlatSpec
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with ServerSyntax
    with Http4sDsl[G] {

  implicit val responseArb: Arbitrary[G[Response[G]]] =
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

  it should "record a span when the response is OK" in {
    val app = HttpRoutes.of[G] { case GET -> Root =>
      Ok()
    }

    evaluateTrace(app, app.orNotFound) { spans =>
      spans.size should be(1)
      spans.head.name should be("GET /")
      spans.head.kind should be(SpanKind.Server)
      spans.head.status should be(SpanStatus.Ok)
    }
  }

  it should "correctly set span status when the server throws an exception" in forAll { errorMsg: String =>
    val app = HttpRoutes.of[G] { case GET -> Root =>
      ApplicativeThrow[G].raiseError(new RuntimeException(errorMsg))
    }

    evaluateTrace(app, app.orNotFound) { spans =>
      spans.size should be(1)
      spans.head.name should be("GET /")
      spans.head.kind should be(SpanKind.Server)
      spans.head.status should be(SpanStatus.Internal(errorMsg))
    }
  }

  it should "correctly set the span status from the http response" in forAll { response: G[Response[G]] =>
    val app = HttpRoutes.of[G] { case GET -> Root =>
      response
    }

    val expectedStatus =
      Http4sStatusMapping.toSpanStatus(unsafeRunK(noopProvideK(response)).status)

    evaluateTrace(app, app.orNotFound) { spans =>
      spans.size should be(1)
      spans.head.name should be("GET /")
      spans.head.kind should be(SpanKind.Server)
      spans.head.status should be(expectedStatus)
    }
  }

  it should "should filter all requests" in forAll { response: G[Response[G]] =>
    val app = HttpRoutes.of[G] { case GET -> Root =>
      response
    }

    evaluateTrace(app, app.orNotFound, { case _ => false }) { spans =>
      spans.size should be(0)
    }
  }

  it should "should filter prometheus and kubernetes endpoints" in {
    val app = HttpRoutes.of[G] {
      case GET -> Root => Ok()
      case GET -> Root / "healthcheck" => Ok()
      case GET -> Root / "liveness" => Ok()
      case GET -> Root / "metrics" => Ok()
    }

    evaluateTrace(
      app,
      app.orNotFound,
      Http4sRequestFilter.kubernetesPrometheus,
      NonEmptyList.of("", "healthcheck", "liveness", "metrics")
    ) { spans =>
      spans.size should be(1)
    }
  }

  it should "should filter arbitrary paths" in forAll(Gen.uuid, Gen.listOf(Gen.uuid)) {
    (first: UUID, others: List[UUID]) =>
      val NEPaths = NonEmptyList(first, others).map(_.toString)
      val paths = NEPaths.toList.toSet.map("/" + _)

      val app = HttpRoutes.of[G] {
        case req if paths.contains(Uri.decode(req.uri.path.toString)) => Ok()
        case GET -> Root => Ok()
      }

      evaluateTrace(
        app,
        app.orNotFound,
        Http4sRequestFilter.fullPaths("/" + NEPaths.head, NEPaths.tail.map("/" + _): _*),
        "/" :: NEPaths
      ) { spans =>
        spans.size should be(1)
      }
  }

  def evaluateTrace(
    routes: HttpRoutes[G],
    app: HttpApp[G],
    filter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
    paths: NonEmptyList[String] = NonEmptyList.one("")
  )(fa: Queue[CompletedSpan] => Assertion): Assertion = {
    val clientDsl = new Http4sClientDsl[F] {}
    import clientDsl._

    def test(f: EntryPoint[F] => HttpApp[F]): Assertion =
      unsafeRunK.apply(
        (for {
          completer <- Resource.eval(RefSpanCompleter[F]("test"))
          ep = EntryPoint[F](SpanSampler.always[F], completer)
        } yield (f(ep), completer))
          .use { case (app, completer) =>
            for {
              _ <- paths.traverse(path => app.run(GET(Uri.unsafeFromString(s"/$path"))).attempt)
              spans <- completer.get
            } yield fa(spans)
          }
      )

    test(injectRoutes(routes, filter, _).orNotFound)
    test(injectApp(app, filter, _))
  }

}
