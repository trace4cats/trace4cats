package trace4cats.model

import cats.data.NonEmptyList
import org.scalacheck.{Arbitrary, Gen}

trait ArbitraryAttributeValues {
  implicit val stringAttributeValueArb: Arbitrary[AttributeValue.StringValue] =
    Arbitrary(Gen.identifier.map(AttributeValue.StringValue(_)))

  implicit val booleanAttributeValueArb: Arbitrary[AttributeValue.BooleanValue] =
    Arbitrary(Gen.oneOf(true, false).map(AttributeValue.BooleanValue(_)))

  implicit val doubleAttributeValueArb: Arbitrary[AttributeValue.DoubleValue] =
    Arbitrary(Gen.double.map(AttributeValue.DoubleValue(_)))

  implicit val longAttributeValueArb: Arbitrary[AttributeValue.LongValue] =
    Arbitrary(Gen.long.map(AttributeValue.LongValue(_)))

  implicit val stringListAttributeValueArb: Arbitrary[AttributeValue.StringList] =
    Arbitrary(
      Gen
        .nonEmptyListOf(Gen.identifier)
        .map(elems => AttributeValue.StringList(NonEmptyList.fromListUnsafe(elems)))
    )
  implicit val booleanListAttributeValueArb: Arbitrary[AttributeValue.BooleanList] =
    Arbitrary(
      Gen
        .nonEmptyListOf(Gen.oneOf(true, false))
        .map(elems => AttributeValue.BooleanList(NonEmptyList.fromListUnsafe(elems)))
    )
  implicit val doubleListAttributeValueArb: Arbitrary[AttributeValue.DoubleList] =
    Arbitrary(
      Gen
        .nonEmptyListOf(Gen.double.suchThat(_.abs > 0.01))
        .map(elems => AttributeValue.DoubleList(NonEmptyList.fromListUnsafe(elems)))
    )
  implicit val longListAttributeValueArb: Arbitrary[AttributeValue.LongList] =
    Arbitrary(Gen.nonEmptyListOf(Gen.long).map(elems => AttributeValue.LongList(NonEmptyList.fromListUnsafe(elems))))

  implicit val attributeValueArb: Arbitrary[AttributeValue] =
    Arbitrary(
      Gen.oneOf(
        stringAttributeValueArb.arbitrary,
        booleanAttributeValueArb.arbitrary,
        doubleAttributeValueArb.arbitrary,
        longAttributeValueArb.arbitrary,
        stringListAttributeValueArb.arbitrary,
        booleanListAttributeValueArb.arbitrary,
        doubleListAttributeValueArb.arbitrary,
        longListAttributeValueArb.arbitrary
      )
    )
}

object ArbitraryAttributeValues extends ArbitraryAttributeValues
