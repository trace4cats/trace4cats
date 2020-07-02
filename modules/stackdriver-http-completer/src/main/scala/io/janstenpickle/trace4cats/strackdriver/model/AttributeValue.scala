package io.janstenpickle.trace4cats.strackdriver.model

import io.circe.{Encoder, Json, JsonObject}

sealed trait AttributeValue
object AttributeValue {
  case class StringValue(value: String) extends AttributeValue
  case class IntValue(value: Long) extends AttributeValue
  case class BoolValue(value: Boolean) extends AttributeValue

  implicit val encoder: Encoder[AttributeValue] = Encoder.instance {
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
