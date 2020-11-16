package io.janstenpickle.trace4cats.collector.common

import io.circe.generic.extras.Configuration

package object config {
  implicit val circeDecoderConfig: Configuration =
    Configuration.default.withDefaults.withKebabCaseMemberNames.withKebabCaseConstructorNames
}
