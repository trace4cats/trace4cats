package io.janstenpickle.trace4cats.stackdriver.model

import io.circe.{Encoder, Json, JsonObject}

sealed trait SDAttributeValue
object SDAttributeValue {
  case class StringValue(value: String) extends SDAttributeValue
  case class IntValue(value: Long) extends SDAttributeValue
  case class BoolValue(value: Boolean) extends SDAttributeValue

  implicit val encoder: Encoder[SDAttributeValue] = Encoder.instance {
    case StringValue(value) =>
      Json.fromJsonObject(
        JsonObject
          .fromMap(
            Map("stringValue" -> Json.fromJsonObject(JsonObject.fromMap(Map("value" -> Json.fromString(value)))))
          )
      )
    case IntValue(value) => Json.fromJsonObject(JsonObject.fromMap(Map("intValue" -> Json.fromLong(value))))
    case BoolValue(value) => Json.fromJsonObject(JsonObject.fromMap(Map("boolValue" -> Json.fromBoolean(value))))

  }
}
