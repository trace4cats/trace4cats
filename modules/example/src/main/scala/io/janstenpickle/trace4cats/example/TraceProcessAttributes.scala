package io.janstenpickle.trace4cats.example

import cats.effect.{ExitCode, IO, IOApp}
import io.janstenpickle.trace4cats.attributes.{EnvironmentAttributes, HostAttributes, SystemPropertyAttributes}
import io.janstenpickle.trace4cats.model.AttributeValue.{LongValue, StringValue}
import io.janstenpickle.trace4cats.model.TraceProcessBuilder

object TraceProcessAttributes extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    TraceProcessBuilder("some-service")
      .withAttributes(EnvironmentAttributes.includeKeys[IO](Set("HOME")))
      .withAttributes(SystemPropertyAttributes.filterKeys[IO](_.contains("xyz")))
      .withAttributes(HostAttributes[IO])
      .withAttributes(IO(Map("side" -> StringValue("effect"))))
      .withAttributes("pure" -> LongValue(1))
      .build
      .flatMap(attrs => IO(println(attrs)))
      .as(ExitCode.Success)
}
