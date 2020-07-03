package io.janstenpickle.trace4cats.strackdriver.http

import cats.ApplicativeError
import cats.effect.{Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.JsonObject
import io.janstenpickle.trace4cats.strackdriver.model.Batch
import io.janstenpickle.trace4cats.strackdriver.oauth.AccessToken
import org.http4s.Method._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Status, Uri}

import scala.util.control.NoStackTrace
import scala.concurrent.duration._
import fs2.Stream

trait CloudTraceClient[F[_]] {
  def submitBatch(batch: Batch): F[Unit]
}

object CloudTraceClient {
  case class UnexpectedResponse(status: Status, message: String) extends RuntimeException with NoStackTrace {
    override def getMessage: String =
      s"""HTTP Status: ${status.toString()}
         |
         |$message
         |""".stripMargin
  }

  private final val base = "https://cloudtrace.googleapis.com/v2/projects"

  def apply[F[_]: Sync: Timer](projectId: String, client: Client[F], tokenF: F[AccessToken]): F[CloudTraceClient[F]] =
    ApplicativeError[F, Throwable].fromEither(Uri.fromString(s"$base/$projectId/traces:batchWrite")).map { uri =>
      object dsl extends Http4sClientDsl[F]
      import dsl._

      new CloudTraceClient[F] {
        override def submitBatch(batch: Batch): F[Unit] =
          for {
            token <- tokenF
            req <- POST(
              batch,
              uri.withQueryParam("access_token", token.accessToken),
              `Content-Type`(MediaType.application.json)
            )
            _ <- Stream
              .retry(
                client.expectOr[JsonObject](req)(resp => resp.as[String].map(UnexpectedResponse(resp.status, _))),
                10.millis,
                _ + 5.millis,
                2
              )
              .compile
              .drain
          } yield ()
      }
    }
}
