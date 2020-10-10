package io.janstenpickle.trace4cats.opentelemetry.common

import io.janstenpickle.trace4cats.model.AttributeValue
import io.janstenpickle.trace4cats.model.AttributeValue._
import io.opentelemetry.common.{AttributeConsumer, AttributeKey, AttributeType, ReadableAttributes}

object Trace4CatsReadableAttributes {

  def apply(map: Map[String, AttributeValue]): ReadableAttributes = new ReadableAttributes {
    override def get[T](key: AttributeKey[T]): T = {

      (key.getType match {
        case AttributeType.STRING => map.get(key.getKey).collect { case StringValue(value) => value.asInstanceOf[T] }
        case AttributeType.BOOLEAN => map.get(key.getKey).collect { case BooleanValue(value) => value.asInstanceOf[T] }
        case AttributeType.LONG => map.get(key.getKey).collect { case LongValue(value) => value.asInstanceOf[T] }
        case AttributeType.DOUBLE => map.get(key.getKey).collect { case DoubleValue(value) => value.asInstanceOf[T] }
        case AttributeType.STRING_ARRAY => Option.empty[T]
        case AttributeType.BOOLEAN_ARRAY => Option.empty[T]
        case AttributeType.LONG_ARRAY => Option.empty[T]
        case AttributeType.DOUBLE_ARRAY => Option.empty[T]
      }).get
    }

    override val size: Int = map.size

    override val isEmpty: Boolean = map.isEmpty

    override def forEach(consumer: AttributeConsumer): Unit = map.foreach {
      case (k, v) =>
        v match {
          case StringValue(value) => consumer.consume[java.lang.String](AttributeKey.stringKey(k), value)
          case BooleanValue(value) => consumer.consume[java.lang.Boolean](AttributeKey.booleanKey(k), value)
          case DoubleValue(value) => consumer.consume[java.lang.Double](AttributeKey.doubleKey(k), value)
          case LongValue(value) => consumer.consume[java.lang.Long](AttributeKey.longKey(k), value)
        }
    }
  }
}
