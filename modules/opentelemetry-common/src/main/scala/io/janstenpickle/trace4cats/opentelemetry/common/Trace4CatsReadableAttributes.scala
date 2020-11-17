package io.janstenpickle.trace4cats.opentelemetry.common

import io.janstenpickle.trace4cats.model.AttributeValue
import io.janstenpickle.trace4cats.model.AttributeValue._
import io.opentelemetry.common.{AttributeConsumer, AttributeKey, AttributeType, ReadableAttributes}

import scala.jdk.CollectionConverters._

object Trace4CatsReadableAttributes {

  def apply(map: Map[String, AttributeValue]): ReadableAttributes =
    new ReadableAttributes {
      override def get[T](key: AttributeKey[T]): T = {

        (key.getType match {
          case AttributeType.STRING =>
            map.get(key.getKey).collect { case StringValue(value) => value.value.asInstanceOf[T] }
          case AttributeType.BOOLEAN =>
            map.get(key.getKey).collect { case BooleanValue(value) => value.value.asInstanceOf[T] }
          case AttributeType.LONG =>
            map.get(key.getKey).collect { case LongValue(value) => value.value.asInstanceOf[T] }
          case AttributeType.DOUBLE =>
            map.get(key.getKey).collect { case DoubleValue(value) => value.value.asInstanceOf[T] }
          case AttributeType.STRING_ARRAY =>
            map.get(key.getKey).collect { case StringList(value) => value.value.toList.asJava.asInstanceOf[T] }
          case AttributeType.BOOLEAN_ARRAY =>
            map.get(key.getKey).collect { case BooleanList(value) => value.value.toList.asJava.asInstanceOf[T] }
          case AttributeType.LONG_ARRAY =>
            map.get(key.getKey).collect { case LongList(value) => value.value.toList.asJava.asInstanceOf[T] }
          case AttributeType.DOUBLE_ARRAY =>
            map.get(key.getKey).collect { case DoubleList(value) => value.value.toList.asJava.asInstanceOf[T] }
        }).get
      }

      override val size: Int = map.size

      override val isEmpty: Boolean = map.isEmpty

      override def forEach(consumer: AttributeConsumer): Unit =
        map.foreach { case (k, v) =>
          v match {
            case StringValue(value) => consumer.consume[java.lang.String](AttributeKey.stringKey(k), value.value)
            case BooleanValue(value) => consumer.consume[java.lang.Boolean](AttributeKey.booleanKey(k), value.value)
            case DoubleValue(value) => consumer.consume[java.lang.Double](AttributeKey.doubleKey(k), value.value)
            case LongValue(value) => consumer.consume[java.lang.Long](AttributeKey.longKey(k), value.value)
            case StringList(value) =>
              consumer.consume[java.util.List[java.lang.String]](
                AttributeKey.stringArrayKey(k),
                value.value.toList.asJava.asInstanceOf[java.util.List[java.lang.String]]
              )
            case BooleanList(value) =>
              consumer.consume[java.util.List[java.lang.Boolean]](
                AttributeKey.booleanArrayKey(k),
                value.value.toList.asJava.asInstanceOf[java.util.List[java.lang.Boolean]]
              )
            case DoubleList(value) =>
              consumer.consume[java.util.List[java.lang.Double]](
                AttributeKey.doubleArrayKey(k),
                value.value.toList.asJava.asInstanceOf[java.util.List[java.lang.Double]]
              )
            case LongList(value) =>
              consumer.consume[java.util.List[java.lang.Long]](
                AttributeKey.longArrayKey(k),
                value.value.toList.asJava.asInstanceOf[java.util.List[java.lang.Long]]
              )
          }
        }
    }
}
