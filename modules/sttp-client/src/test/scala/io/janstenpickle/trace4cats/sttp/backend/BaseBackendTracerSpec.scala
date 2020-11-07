package io.janstenpickle.trace4cats.sttp.backend

import cats.data.NonEmptyList
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Sync, Timer}
import cats.implicits._
import cats.{~>, Eq, Id}
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.inject.{EntryPoint, Provide, Trace}
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{Challenge, HttpApp, HttpRoutes, Response}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sttp.client._
import sttp.client.http4s.Http4sBackend
import sttp.model.StatusCode

import scala.concurrent.duration._

abstract class BaseBackendTracerSpec[F[_]: ConcurrentEffect: ContextShift, G[_]: Sync: Trace](
  port: Int,
  fkId: F ~> Id,
  liftBackend: SttpBackend[F, Nothing, NothingT] => SttpBackend[G, Nothing, NothingT],
  timer: Timer[F]
)(implicit provide: Provide[F, G])
    extends AnyFlatSpec
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with Http4sDsl[F] {

  implicit val t: Timer[F] = timer

  implicit val responseArb: Arbitrary[Response[F]] =
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
        ).map(fkId.apply)
      )
    )

  it should "correctly set request headers and span status when the response body is read" in test(_.send(_).void)

  def test(
    runReq: (SttpBackend[G, Nothing, NothingT], Request[Either[String, String], Nothing]) => G[Unit]
  ): Assertion =
    forAll { (rootSpan: String, req1Span: String, req2Span: String, response: Response[F]) =>
      val rootSpanName = s"root: $rootSpan"
      val req1SpanName = s"req1: $req1Span"
      val req2SpanName = s"req2: $req2Span"

      val (httpApp, headersRef) = makeHttpApp(response)

      fkId(withRunningHttpServer(httpApp) { port =>
        RefSpanCompleter[F]("test").flatMap {
          completer =>
            withBackend {
              backend =>
                def req(body: String): G[Unit] =
                  runReq(backend, basicRequest.get(uri"http://localhost:$port").body(body))

                for {
                  _ <- entryPoint(completer)
                    .root(rootSpanName)
                    .use(
                      provide(
                        Trace[G]
                          .span(req1SpanName)(req(req1SpanName))
                          .handleError(_ => ()) >> Trace[G].span(req2SpanName)(req(req2SpanName)).handleError(_ => ())
                      )
                    )
                  spans <- completer.get
                  headersMap <- headersRef.get
                } yield {
                  (spans.toList.map(_.name) should contain)
                    .theSameElementsAs(List("GET ", "GET ", rootSpanName, req1SpanName, req2SpanName))
                  (headersMap.keys should contain).theSameElementsAs(Set(req1SpanName, req2SpanName))

                  assert(
                    Eq.eqv(
                      ToHeaders.w3c.toContext(headersMap(req1SpanName)).get.spanId,
                      spans.toList.sortBy(_.`end`.toEpochMilli).find(_.name == "GET ").get.context.spanId
                    )
                  )

                  assert(
                    Eq.eqv(
                      ToHeaders.w3c.toContext(headersMap(req2SpanName)).get.spanId,
                      spans.toList.sortBy(_.`end`.toEpochMilli).reverse.find(_.name == "GET ").get.context.spanId
                    )
                  )

                  val expectedStatus =
                    SttpStatusMapping.statusToSpanStatus(response.status.reason, StatusCode(response.status.code))
                  (spans.toList.collect {
                    case span if span.name == "GET " => span.status
                  } should contain).theSameElementsAs(List.fill(2)(expectedStatus))

                }
            }
        }

      })
    }

  def entryPoint(completer: SpanCompleter[F]): EntryPoint[F] = EntryPoint[F](SpanSampler.always[F], completer)

  def makeHttpApp(resp: Response[F]): (HttpApp[F], Ref[F, Map[String, Map[String, String]]]) = {
    val headersRef = Ref.unsafe[F, Map[String, Map[String, String]]](Map.empty)

    HttpRoutes
      .of[F] {
        case req @ GET -> Root =>
          req
            .as[String]
            .flatMap { key =>
              headersRef.update(
                _.updated(
                  key,
                  req.headers.toList.map { header =>
                    header.name.value -> header.value
                  }.toMap
                )
              )
            }
            .as(resp)
      }
      .orNotFound -> headersRef
  }

  def withBackend(fa: SttpBackend[G, Nothing, NothingT] => F[Assertion]): F[Assertion] =
    Blocker[F]
      .use { blocker =>
        BlazeClientBuilder[F](blocker.blockingContext).resource.use { client =>
          fa(liftBackend(Http4sBackend.usingClient(client, blocker)))
        }
      }

  def withRunningHttpServer(app: HttpApp[F])(fa: Int => F[Assertion]): F[Assertion] = {
    Blocker[F]
      .flatMap { blocker =>
        BlazeServerBuilder[F](blocker.blockingContext)
          .bindHttp(port, "localhost")
          .withHttpApp(app)
          .resource

      }
      .use { _ =>
        fa(port).flatTap(_ => timer.sleep(100.millis))
      }
  }

}
