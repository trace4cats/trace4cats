package io.janstenpickle.trace4cats.http4s.client

import java.util.concurrent.Executors

import cats.Eq
import cats.data.{Kleisli, NonEmptyList}
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ContextShift, IO, Timer}
import cats.implicits._
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.http4s.common.Http4sStatusMapping
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ClientSyntaxSpec
    extends AnyFlatSpec
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with ClientSyntax
    with Http4sClientDsl[Kleisli[IO, Span[IO], *]]
    with Http4sDsl[IO] {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))

  type TraceIO[A] = Kleisli[IO, Span[IO], A]

  implicit val responseArb: Arbitrary[Response[IO]] =
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
        ).map(_.unsafeRunSync())
      )
    )

  it should "correctly set request headers and span status when the response body is read" in test(
    _.expect[String](_).void
  )

  it should "correctly set request headers and span status when  the response body is not read" in test(
    _.status(_).void
  )

  def test(runReq: (Client[TraceIO], TraceIO[Request[TraceIO]]) => TraceIO[Unit]): Assertion =
    forAll { (rootSpan: String, req1Span: String, req2Span: String, response: Response[IO]) =>
      val rootSpanName = s"root: $rootSpan"
      val req1SpanName = s"req1: $req1Span"
      val req2SpanName = s"req2: $req2Span"

      val (httpApp, headersRef) = makeHttpApp(response)

      withRunningHttpServer(httpApp) { port =>
        RefSpanCompleter[IO].flatMap { completer =>
          withClient { client =>
            def req(body: String): TraceIO[Unit] =
              runReq(client, GET(body, Uri.unsafeFromString(s"http://localhost:$port")))

            for {
              _ <- entryPoint(completer).root(rootSpanName).use { root =>
                (Trace[TraceIO]
                  .span(req1SpanName)(req(req1SpanName))
                  .handleError(_ => ()) >> Trace[TraceIO].span(req2SpanName)(req(req2SpanName)).handleError(_ => ()))
                  .run(root)
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

              val expectedStatus = Http4sStatusMapping.toSpanStatus(response.status)
              (spans.toList.collect {
                case span if span.name == "GET " => span.status
              } should contain).theSameElementsAs(List.fill(2)(expectedStatus))

            }
          }
        }

      }.unsafeRunSync()
    }

  def entryPoint(completer: SpanCompleter[IO]): EntryPoint[IO] = EntryPoint[IO](SpanSampler.always, completer)

  def makeHttpApp(resp: Response[IO]): (HttpApp[IO], Ref[IO, Map[String, Map[String, String]]]) = {
    val headersRef = Ref.unsafe[IO, Map[String, Map[String, String]]](Map.empty)

    HttpRoutes
      .of[IO] {
        case req @ GET -> Root =>
          req
            .as[String]
            .flatMap { key =>
              headersRef.update(_.updated(key, req.headers.toList.map { header =>
                header.name.value -> header.value
              }.toMap))
            }
            .as(resp)
      }
      .orNotFound -> headersRef
  }

  def withClient(fa: Client[TraceIO] => IO[Assertion]): IO[Assertion] =
    Blocker[IO]
      .flatMap { blocker =>
        BlazeClientBuilder[IO](blocker.blockingContext).resource
      }
      .use { client =>
        fa(client.liftTrace())
      }

  def withRunningHttpServer(app: HttpApp[IO])(fa: Int => IO[Assertion]): IO[Assertion] = {
    val port = 8083

    Blocker[IO]
      .flatMap { blocker =>
        BlazeServerBuilder[IO](blocker.blockingContext)
          .bindHttp(port, "localhost")
          .withHttpApp(app)
          .resource

      }
      .use { _ =>
        fa(port).flatTap(_ => timer.sleep(100.millis))
      }
  }

}
