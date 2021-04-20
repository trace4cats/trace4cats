package io.janstenpickle.trace4cats.collector.common

import java.net.http.HttpClient
import java.util.concurrent.Executor

import cats.effect.{ConcurrentEffect, Sync}
import org.http4s.client.Client
import org.http4s.client.jdkhttpclient.JdkHttpClient

object Http4sJdkClient {
  def apply[F[_]: ConcurrentEffect: ContextShift]: F[Client[F]] = {
    def blockerExecutor: Executor =
      new Executor {
        override def execute(command: Runnable): Unit =
          blocker.blockingContext.execute(command)
      }

    Sync[F].delay(JdkHttpClient[F](HttpClient.newBuilder().executor(blockerExecutor).build()))
  }
}
