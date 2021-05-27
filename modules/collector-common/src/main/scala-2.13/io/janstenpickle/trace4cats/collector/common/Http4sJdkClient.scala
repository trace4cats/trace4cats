package io.janstenpickle.trace4cats.collector.common

import cats.effect.kernel.{Async, Resource}
import cats.syntax.applicative._
import org.http4s.client.Client
import org.http4s.jdkhttpclient.JdkHttpClient

import java.net.http.HttpClient
import scala.concurrent.ExecutionContext

object Http4sJdkClient {
  def apply[F[_]: Async](ec: Option[ExecutionContext] = None): Resource[F, Client[F]] =
    for {
      ec <- Resource.eval(ec.fold(Async[F].executionContext)(_.pure))
      jdkClient <- Resource.eval(Async[F].delay(HttpClient.newBuilder().executor(ec.execute _).build()))
      client <- JdkHttpClient[F](jdkClient)
    } yield client
}
