package io.janstenpickle.trace4cats

import io.janstenpickle.trace4cats.model.AttributeValue

package object filtering {
  type AttributeFilter = (String, AttributeValue) => Boolean
}
