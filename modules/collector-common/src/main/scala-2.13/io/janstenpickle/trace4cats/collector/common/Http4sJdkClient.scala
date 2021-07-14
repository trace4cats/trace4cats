package io.janstenpickle.trace4cats.collector.common

import java.net.http.HttpClient
import java.util.concurrent.Executor

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Sync}
import org.http4s.client.Client
import org.http4s.jdkhttpclient.JdkHttpClient

object Http4sJdkClient {
  def apply[F[_]: ConcurrentEffect: ContextShift](blocker: Blocker): F[Client[F]] = {
    def blockerExecutor: Executor =
      new Executor {
        override def execute(command: Runnable): Unit =
          blocker.blockingContext.execute(command)
      }

    Sync[F].delay(JdkHttpClient[F](HttpClient.newBuilder().executor(blockerExecutor).build()))
  }
}
