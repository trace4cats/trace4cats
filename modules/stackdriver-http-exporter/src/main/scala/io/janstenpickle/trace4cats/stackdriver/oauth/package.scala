package io.janstenpickle.trace4cats.stackdriver

import io.circe.generic.extras.Configuration

package object oauth {
  implicit val circeConfig: Configuration = Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames

}
