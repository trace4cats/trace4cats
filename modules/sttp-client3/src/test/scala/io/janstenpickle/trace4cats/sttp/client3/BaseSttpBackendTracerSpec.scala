package io.janstenpickle.trace4cats.sttp.client3

import cats.data.NonEmptyList
import cats.effect.kernel.{Async, Ref, Sync}
import cats.implicits._
import cats.{~>, Eq, Id}
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.http4s.common.Http4sHeaders
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model.TraceHeaders
import io.janstenpickle.trace4cats.sttp.common.SttpStatusMapping
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.{Challenge, HttpApp, HttpRoutes, Response}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sttp.client3._
import sttp.client3.http4s.Http4sBackend
import sttp.model.StatusCode

abstract class BaseSttpBackendTracerSpec[F[_]: Async, G[_]: Sync: Trace, Ctx](
  unsafeRunK: F ~> Id,
  makeSomeContext: Span[F] => Ctx,
  liftBackend: SttpBackend[F, Any] => SttpBackend[G, Any]
)(implicit P: Provide[F, G, Ctx])
    extends AnyFlatSpec
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with Http4sDsl[F] {

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
        ).map(unsafeRunK.apply)
      )
    )

  it should "correctly set request headers and span status when the response body is read" in test(_.send(_).void)

  def test(runReq: (SttpBackend[G, Any], Request[Either[String, String], Any]) => G[Unit]): Assertion =
    forAll { (rootSpan: String, req1Span: String, req2Span: String, response: Response[F]) =>
      val rootSpanName = s"root: $rootSpan"
      val req1SpanName = s"req1: $req1Span"
      val req2SpanName = s"req2: $req2Span"

      val (httpApp, headersRef) = makeHttpApp(response)

      unsafeRunK(RefSpanCompleter[F]("test").flatMap { completer =>
        withBackend(httpApp) { backend =>
          def req(body: String): G[Unit] =
            runReq(backend, basicRequest.get(uri"http:///").body(body))

          for {
            _ <- entryPoint(completer)
              .root(rootSpanName)
              .use { span =>
                P.provideK(makeSomeContext(span))(
                  Trace[G]
                    .span(req1SpanName)(req(req1SpanName))
                    .handleError(_ => ()) >> Trace[G].span(req2SpanName)(req(req2SpanName)).handleError(_ => ())
                )
              }
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
      })
    }

  def entryPoint(completer: SpanCompleter[F]): EntryPoint[F] = EntryPoint[F](SpanSampler.always[F], completer)

  def makeHttpApp(resp: Response[F]): (HttpApp[F], Ref[F, Map[String, TraceHeaders]]) = {
    val headersRef = Ref.unsafe[F, Map[String, TraceHeaders]](Map.empty)

    HttpRoutes
      .of[F] { case req @ GET -> Root =>
        req
          .as[String]
          .flatMap { key =>
            headersRef.update(_.updated(key, Http4sHeaders.converter.from(req.headers)))
          }
          .as(resp)
      }
      .orNotFound -> headersRef
  }

  def withBackend(app: HttpApp[F])(fa: SttpBackend[G, Any] => F[Assertion]): F[Assertion] =
    fa(liftBackend(Http4sBackend.usingClient(Client.fromHttpApp(app))))

}
