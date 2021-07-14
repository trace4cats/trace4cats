package io.janstenpickle.trace4cats.sttp.tapir

import cats.data.EitherT
import cats.effect.{BracketThrow, Sync}
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import io.circe.generic.auto._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.sttp.tapir.model.{Device, ErrorInfo, ErrorInfoException, Vendor}
import io.janstenpickle.trace4cats.sttp.tapir.syntax._
import sttp.model.Headers
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint

import java.util.UUID

class Endpoints[F[_]: Sync, G[_]: BracketThrow: Trace] {
  val devices =
    endpoint.get
      .in("devices" / path[Int]("deviceId"))
      .in(header[Option[String]]("X-Auth-Token"))
      .in(headers.map(Headers.apply _)(_.headers.toList))
      .out(customJsonBody[Device])
      .errorOut(ErrorInfo.endpointOutput)
      .serverLogic { case (id, _, _) =>
        //some logic that returns Either[E, O]
        Either
          .cond(id > 0, Device(id, "processor", 11), ErrorInfo.NotFound(s"deviceId=$id"): ErrorInfo)
          .pure[G]
      }

  val vendors =
    endpoint.get
      .in("vendors" / path[Int]("vendorId"))
      .in(header[Option[String]]("X-Auth-Token"))
      .in(headers.map(Headers.apply _)(_.headers.toList))
      .out(customJsonBody[Vendor])
      .errorOut(ErrorInfoException.endpointOutput)
      .serverLogicRecoverErrors { case (id, _, _) =>
        //some logic that raises errors via MonadThrow
        Either
          .cond(id > 0, Vendor(id, "Impel"), ErrorInfoException.NotFound(s"vendorId=$id"): ErrorInfoException)
          .liftTo[G]
      }

  def mkContextOrErr(auth: Option[String], span: Span[F]): F[Either[ErrorInfo, TraceContext[F]]] =
    EitherT
      .fromOption[F](auth, ErrorInfo.Unauthorized("catalog"): ErrorInfo)
      .semiflatMap(_ => Sync[F].delay(UUID.randomUUID().toString).map(TraceContext(_, span)))
      .value

  def mkContextOrRaise(auth: Option[String], span: Span[F]): F[TraceContext[F]] =
    auth.liftTo[F](ErrorInfoException.Unauthorized("catalog"): ErrorInfoException) >>
      Sync[F].delay(UUID.randomUUID().toString).map(TraceContext(_, span))

  def tracedEndpoints(
    entryPoint: EntryPoint[F]
  )(implicit P: Provide[F, G, Span[F]]): List[ServerEndpoint[_, _, _, Any, F]] = List(
    devices.inject(
      entryPoint,
      _._3,
      errorToSpanStatus = TapirStatusMapping.errorShowToSpanStatus(ErrorInfo.statusCodeGetter)
    ),
    vendors.inject(
      entryPoint,
      _._3,
      errorToSpanStatus = TapirStatusMapping.errorMessageToSpanStatus(ErrorInfoException.statusCodeGetter)
    )
  )

  def tracedContextEndpoints(
    entryPoint: EntryPoint[F]
  )(implicit P: Provide[F, G, TraceContext[F]]): List[ServerEndpoint[_, _, _, Any, F]] = List(
    devices.injectContext(
      entryPoint,
      (in, s) => mkContextOrErr(in._2, s),
      _._3,
      errorToSpanStatus = TapirStatusMapping.errorShowToSpanStatus(ErrorInfo.statusCodeGetter)
    ),
    vendors.injectContextRecoverErrors(
      entryPoint,
      (in, s) => mkContextOrRaise(in._2, s),
      _._3,
      errorToSpanStatus = TapirStatusMapping.errorMessageToSpanStatus(ErrorInfoException.statusCodeGetter)
    )
  )
}
