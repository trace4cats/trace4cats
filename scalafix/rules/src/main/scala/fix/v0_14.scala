package fix

import scalafix.v1._
import scala.meta._

class v0_14 extends SemanticRule("v0_14") {

  val oldBasePackage = "io.janstenpickle.trace4cats"

  val replacements =
    replacement(s"$oldBasePackage.kernel", "trace4cats.kernel") ++ replacement(
      s"$oldBasePackage.model",
      "trace4cats.model"
    ) ++ replacement(s"$oldBasePackage.model.AttributeValue", "trace4cats.model.AttributeValue") ++ replacement(
      Set(oldBasePackage, s"$oldBasePackage.inject", s"$oldBasePackage.`export`"),
      "trace4cats",
      Map("HotswapSpanCompleter" -> "trace4cats.dynamic", "HotswapSpanExporter" -> "trace4cats.dynamic")
    ) ++ replacement(s"$oldBasePackage.attributes", "trace4cats.attributes") ++ replacement(
      s"$oldBasePackage.sampling.dynamic",
      "trace4cats.dynamic"
    ) ++ replacement(s"$oldBasePackage.sampling.dynamic.config", "trace4cats.dynamic.config") ++ replacement(
      s"$oldBasePackage.log",
      "trace4cats.log"
    ) ++ replacement(s"$oldBasePackage.filtering", "trace4cats.filtering") ++ replacement(
      s"$oldBasePackage.sampling.tail",
      "trace4cats.sampling.tail"
    ) ++ replacement(
      Set(s"$oldBasePackage.rate.sampling"),
      "trace4cats",
      Map("RateTailSpanSampler" -> "trace4cats.sampling.tail")
    ) ++ replacement(s"$oldBasePackage.base.context", "trace4cats.context") ++ replacement(
      s"$oldBasePackage.base.optics",
      "trace4cats.optics"
    ) ++ replacement(s"$oldBasePackage.test", "trace4cats.test") ++ replacement(
      s"$oldBasePackage.meta",
      "trace4cats.meta"
    ) ++ replacement(s"$oldBasePackage.http4s.server", "trace4cats.http4s.server") ++ replacement(
      s"$oldBasePackage.http4s.server.syntax",
      "trace4cats.http4s.server.syntax"
    ) ++ replacement(s"$oldBasePackage.http4s.client", "trace4cats.http4s.client") ++ replacement(
      s"$oldBasePackage.http4s.client.syntax",
      "trace4cats.http4s.client.syntax"
    ) ++ replacement(
      s"$oldBasePackage.natchez.conversions.toNatchez",
      "trace4cats.natchez.conversions.toNatchez"
    ) ++ replacement(
      s"$oldBasePackage.natchez.conversions.fromNatchez",
      "trace4cats.natchez.conversions.fromNatchez"
    ) ++ replacement(s"$oldBasePackage.avro", "trace4cats.avro") ++ replacement(
      s"$oldBasePackage.avro.kafka",
      "trace4cats.avro.kafka"
    ) ++ replacement(s"$oldBasePackage.jaeger", "trace4cats.jaeger") ++ replacement(
      s"$oldBasePackage.opentelemetry.otlp",
      "trace4cats.opentelemetry.otlp"
    ) ++ replacement(s"$oldBasePackage.opentelemetry.jaeger", "trace4cats.opentelemetry.jaeger") ++ replacement(
      s"$oldBasePackage.stackdriver",
      "trace4cats.stackdriver"
    ) ++ replacement(s"$oldBasePackage.stackdriver.oauth", "trace4cats.stackdriver.oauth") ++ replacement(
      s"$oldBasePackage.stackdriver.project",
      "trace4cats.stackdriver.project"
    ) ++ replacement(s"$oldBasePackage.datadog", "trace4cats.datadog") ++ replacement(
      s"$oldBasePackage.newrelic",
      "trace4cats.newrelic"
    ) ++ replacement(s"$oldBasePackage.zipkin", "trace4cats.zipkin") ++ replacement(
      s"$oldBasePackage.kafka",
      "trace4cats.kafka"
    ) ++ replacement(s"$oldBasePackage.kafka.syntax", "trace4cats.kafka.syntax")

  def replacement(oldPackage: String, newPackage: String): Map[String, (String, Map[String, String])] =
    replacement(Set(oldPackage), newPackage)

  def replacement(
    oldPackages: Set[String],
    newPackage: String,
    exceptions: Map[String, String] = Map.empty
  ): Map[String, (String, Map[String, String])] =
    oldPackages.map(_ -> (newPackage, exceptions)).toMap

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect { case Importer(ref, importees) =>
      replacements.get(ref.toString()).fold(Patch.empty) { case (replacement, exceptions) =>
        importees
          .map(i => exceptions.get(i.toString()).fold(Patch.replaceTree(ref, replacement))(Patch.replaceTree(ref, _)))
          .asPatch
      }
    }.asPatch
}
