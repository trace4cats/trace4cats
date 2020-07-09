package io.janstenpickle.trace4cats.collector

import cats.effect.{Blocker, ExitCode, IO}
import com.monovore.decline._
import com.monovore.decline.effect._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.collector.common.CommonCollector

object CollectorLite
    extends CommandIOApp(
      name = "trace4cats-collector-lite",
      header = "Trace 4 Cats Collector Lite Edition",
      version = "0.1.0"
    ) {

  override def main: Opts[IO[ExitCode]] =
    CommonCollector[IO].map { collector =>
      Blocker[IO].use { blocker =>
        for {
          logger <- Slf4jLogger.create[IO]
          exitCode <- collector.run((blocker, logger, List.empty)).use(_.compile.drain.as(ExitCode.Success))
        } yield exitCode
      }
    }

}
