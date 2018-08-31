/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.catalyst

import java.sql.{Date, Timestamp}

import scala.reflect.runtime.universe.TypeTag

import org.apache.spark.SparkFunSuite
import org.apache.spark.sql.catalyst.analysis.UnresolvedExtractValue
import org.apache.spark.sql.catalyst.expressions.{CreateNamedStruct, Expression, If, SpecificInternalRow, UpCast}
import org.apache.spark.sql.catalyst.expressions.objects.{AssertNotNull, NewInstance}
import org.apache.spark.sql.types._

case class PrimitiveData(
    intField: Int,
    longField: Long,
    doubleField: Double,
    floatField: Float,
    shortField: Short,
    byteField: Byte,
    booleanField: Boolean)

case class NullableData(
    intField: java.lang.Integer,
    longField: java.lang.Long,
    doubleField: java.lang.Double,
    floatField: java.lang.Float,
    shortField: java.lang.Short,
    byteField: java.lang.Byte,
    booleanField: java.lang.Boolean,
    stringField: String,
    decimalField: java.math.BigDecimal,
    dateField: Date,
    timestampField: Timestamp,
    binaryField: Array[Byte])

case class OptionalData(
    intField: Option[Int],
    longField: Option[Long],
    doubleField: Option[Double],
    floatField: Option[Float],
    shortField: Option[Short],
    byteField: Option[Byte],
    booleanField: Option[Boolean],
    structField: Option[PrimitiveData])

case class ComplexData(
    arrayField: Seq[Int],
    arrayField1: Array[Int],
    arrayField2: List[Int],
    arrayFieldContainsNull: Seq[java.lang.Integer],
    mapField: Map[Int, Long],
    mapFieldValueContainsNull: Map[Int, java.lang.Long],
    structField: PrimitiveData,
    nestedArrayField: Array[Array[Int]])

case class GenericData[A](
    genericField: A)

object GenericData {
  type IntData = GenericData[Int]
}

case class MultipleConstructorsData(a: Int, b: String, c: Double) {
  def this(b: String, a: Int) = this(a, b, c = 1.0)
}

case class SpecialCharAsFieldData(`field.1`: String, `field 2`: String)

object TestingUDT {
  @SQLUserDefinedType(udt = classOf[NestedStructUDT])
  class NestedStruct(val a: Integer, val b: Long, val c: Double)

  class NestedStructUDT extends UserDefinedType[NestedStruct] {
    override def sqlType: DataType = new StructType()
      .add("a", IntegerType, nullable = true)
      .add("b", LongType, nullable = false)
      .add("c", DoubleType, nullable = false)

    override def serialize(n: NestedStruct): Any = {
      val row = new SpecificInternalRow(sqlType.asInstanceOf[StructType].map(_.dataType))
      row.setInt(0, n.a)
      row.setLong(1, n.b)
      row.setDouble(2, n.c)
    }

    override def userClass: Class[NestedStruct] = classOf[NestedStruct]

    override def deserialize(datum: Any): NestedStruct = datum match {
      case row: InternalRow =>
        new NestedStruct(row.getInt(0), row.getLong(1), row.getDouble(2))
    }
  }
}

object TestingValueClass {
  case class IntWrapper(i: Int) extends AnyVal
  case class StrWrapper(s: String) extends AnyVal

  case class ValueClassData(
    intField: Int,
    wrappedInt: IntWrapper,
    strField: String,
    wrappedStr: StrWrapper)
}

class ScalaReflectionSuite extends SparkFunSuite {
  import org.apache.spark.sql.catalyst.ScalaReflection._

  // A helper method used to test `ScalaReflection.serializerForType`.
  private def serializerFor[T: TypeTag]: Expression =
    serializerForType(ScalaReflection.localTypeOf[T])

  // A helper method used to test `ScalaReflection.deserializerForType`.
  private def deserializerFor[T: TypeTag]: Expression =
    deserializerForType(ScalaReflection.localTypeOf[T])

  test("SQLUserDefinedType annotation on Scala structure") {
    val schema = schemaFor[TestingUDT.NestedStruct]
    assert(schema === Schema(
      new TestingUDT.NestedStructUDT,
      nullable = true
    ))
  }

  test("primitive data") {
    val schema = schemaFor[PrimitiveData]
    assert(schema === Schema(
      StructType(Seq(
        StructField("intField", IntegerType, nullable = false),
        StructField("longField", LongType, nullable = false),
        StructField("doubleField", DoubleType, nullable = false),
        StructField("floatField", FloatType, nullable = false),
        StructField("shortField", ShortType, nullable = false),
        StructField("byteField", ByteType, nullable = false),
        StructField("booleanField", BooleanType, nullable = false))),
      nullable = true))
  }

  test("nullable data") {
    val schema = schemaFor[NullableData]
    assert(schema === Schema(
      StructType(Seq(
        StructField("intField", IntegerType, nullable = true),
        StructField("longField", LongType, nullable = true),
        StructField("doubleField", DoubleType, nullable = true),
        StructField("floatField", FloatType, nullable = true),
        StructField("shortField", ShortType, nullable = true),
        StructField("byteField", ByteType, nullable = true),
        StructField("booleanField", BooleanType, nullable = true),
        StructField("stringField", StringType, nullable = true),
        StructField("decimalField", DecimalType.SYSTEM_DEFAULT, nullable = true),
        StructField("dateField", DateType, nullable = true),
        StructField("timestampField", TimestampType, nullable = true),
        StructField("binaryField", BinaryType, nullable = true))),
      nullable = true))
  }

  test("optional data") {
    val schema = schemaFor[OptionalData]
    assert(schema === Schema(
      StructType(Seq(
        StructField("intField", IntegerType, nullable = true),
        StructField("longField", LongType, nullable = true),
        StructField("doubleField", DoubleType, nullable = true),
        StructField("floatField", FloatType, nullable = true),
        StructField("shortField", ShortType, nullable = true),
        StructField("byteField", ByteType, nullable = true),
        StructField("booleanField", BooleanType, nullable = true),
        StructField("structField", schemaFor[PrimitiveData].dataType, nullable = true))),
      nullable = true))
  }

  test("complex data") {
    val schema = schemaFor[ComplexData]
    assert(schema === Schema(
      StructType(Seq(
        StructField(
          "arrayField",
          ArrayType(IntegerType, containsNull = false),
          nullable = true),
        StructField(
          "arrayField1",
          ArrayType(IntegerType, containsNull = false),
          nullable = true),
        StructField(
          "arrayField2",
          ArrayType(IntegerType, containsNull = false),
          nullable = true),
        StructField(
          "arrayFieldContainsNull",
          ArrayType(IntegerType, containsNull = true),
          nullable = true),
        StructField(
          "mapField",
          MapType(IntegerType, LongType, valueContainsNull = false),
          nullable = true),
        StructField(
          "mapFieldValueContainsNull",
          MapType(IntegerType, LongType, valueContainsNull = true),
          nullable = true),
        StructField(
          "structField",
          StructType(Seq(
            StructField("intField", IntegerType, nullable = false),
            StructField("longField", LongType, nullable = false),
            StructField("doubleField", DoubleType, nullable = false),
            StructField("floatField", FloatType, nullable = false),
            StructField("shortField", ShortType, nullable = false),
            StructField("byteField", ByteType, nullable = false),
            StructField("booleanField", BooleanType, nullable = false))),
          nullable = true),
        StructField(
          "nestedArrayField",
          ArrayType(ArrayType(IntegerType, containsNull = false), containsNull = true)))),
      nullable = true))
  }

  test("generic data") {
    val schema = schemaFor[GenericData[Int]]
    assert(schema === Schema(
      StructType(Seq(
        StructField("genericField", IntegerType, nullable = false))),
      nullable = true))
  }

  test("tuple data") {
    val schema = schemaFor[(Int, String)]
    assert(schema === Schema(
      StructType(Seq(
        StructField("_1", IntegerType, nullable = false),
        StructField("_2", StringType, nullable = true))),
      nullable = true))
  }

  test("type-aliased data") {
    assert(schemaFor[GenericData[Int]] == schemaFor[GenericData.IntData])
  }

  test("convert PrimitiveData to catalyst") {
    val data = PrimitiveData(1, 1, 1, 1, 1, 1, true)
    val convertedData = InternalRow(1, 1.toLong, 1.toDouble, 1.toFloat, 1.toShort, 1.toByte, true)
    val dataType = schemaFor[PrimitiveData].dataType
    assert(CatalystTypeConverters.createToCatalystConverter(dataType)(data) === convertedData)
  }

  test("convert Option[Product] to catalyst") {
    val primitiveData = PrimitiveData(1, 1, 1, 1, 1, 1, true)
    val data = OptionalData(Some(2), Some(2), Some(2), Some(2), Some(2), Some(2), Some(true),
      Some(primitiveData))
    val dataType = schemaFor[OptionalData].dataType
    val convertedData = InternalRow(2, 2.toLong, 2.toDouble, 2.toFloat, 2.toShort, 2.toByte, true,
      InternalRow(1, 1, 1, 1, 1, 1, true))
    assert(CatalystTypeConverters.createToCatalystConverter(dataType)(data) === convertedData)
  }

  test("infer schema from case class with multiple constructors") {
    val dataType = schemaFor[MultipleConstructorsData].dataType
    dataType match {
      case s: StructType =>
        // Schema should have order: a: Int, b: String, c: Double
        assert(s.fieldNames === Seq("a", "b", "c"))
        assert(s.fields.map(_.dataType) === Seq(IntegerType, StringType, DoubleType))
    }
  }

  test("SPARK-15062: Get correct serializer for List[_]") {
    val list = List(1, 2, 3)
    val serializer = serializerFor[List[Int]]
    assert(serializer.isInstanceOf[NewInstance])
    assert(serializer.asInstanceOf[NewInstance]
      .cls.isAssignableFrom(classOf[org.apache.spark.sql.catalyst.util.GenericArrayData]))
  }

  test("SPARK 16792: Get correct deserializer for List[_]") {
    val listDeserializer = deserializerFor[List[Int]]
    assert(listDeserializer.dataType == ObjectType(classOf[List[_]]))
  }

  test("serialize and deserialize arbitrary sequence types") {
    import scala.collection.immutable.Queue
    val queueSerializer = serializerFor[Queue[Int]]
    assert(queueSerializer.dataType ==
      ArrayType(IntegerType, containsNull = false))
    val queueDeserializer = deserializerFor[Queue[Int]]
    assert(queueDeserializer.dataType == ObjectType(classOf[Queue[_]]))

    import scala.collection.mutable.ArrayBuffer
    val arrayBufferSerializer = serializerFor[ArrayBuffer[Int]]
    assert(arrayBufferSerializer.dataType ==
      ArrayType(IntegerType, containsNull = false))
    val arrayBufferDeserializer = deserializerFor[ArrayBuffer[Int]]
    assert(arrayBufferDeserializer.dataType == ObjectType(classOf[ArrayBuffer[_]]))
  }

  test("serialize and deserialize arbitrary map types") {
    val mapSerializer = serializerFor[Map[Int, Int]]
    assert(mapSerializer.dataType ==
      MapType(IntegerType, IntegerType, valueContainsNull = false))
    val mapDeserializer = deserializerFor[Map[Int, Int]]
    assert(mapDeserializer.dataType == ObjectType(classOf[Map[_, _]]))

    import scala.collection.immutable.HashMap
    val hashMapSerializer = serializerFor[HashMap[Int, Int]]
    assert(hashMapSerializer.dataType ==
      MapType(IntegerType, IntegerType, valueContainsNull = false))
    val hashMapDeserializer = deserializerFor[HashMap[Int, Int]]
    assert(hashMapDeserializer.dataType == ObjectType(classOf[HashMap[_, _]]))

    import scala.collection.mutable.{LinkedHashMap => LHMap}
    val linkedHashMapSerializer = serializerFor[LHMap[Long, String]]
    assert(linkedHashMapSerializer.dataType ==
      MapType(LongType, StringType, valueContainsNull = true))
    val linkedHashMapDeserializer = deserializerFor[LHMap[Long, String]]
    assert(linkedHashMapDeserializer.dataType == ObjectType(classOf[LHMap[_, _]]))
  }

  test("SPARK-22442: Generate correct field names for special characters") {
    val serializer = serializerFor[SpecialCharAsFieldData]
      .collect {
        case If(_, _, s: CreateNamedStruct) => s
      }.head
    val deserializer = deserializerFor[SpecialCharAsFieldData]
    assert(serializer.dataType(0).name == "field.1")
    assert(serializer.dataType(1).name == "field 2")

    val newInstance = deserializer.collect { case n: NewInstance => n }.head

    val argumentsFields = newInstance.arguments.flatMap { _.collect {
      case UpCast(u: UnresolvedExtractValue, _, _) => u.extraction.toString
    }}
    assert(argumentsFields(0) == "field.1")
    assert(argumentsFields(1) == "field 2")
  }

  test("SPARK-22472: add null check for top-level primitive values") {
    assert(deserializerFor[Int].isInstanceOf[AssertNotNull])
    assert(!deserializerFor[String].isInstanceOf[AssertNotNull])
  }

  test("SPARK-23025: schemaFor should support Null type") {
    val schema = schemaFor[(Int, Null)]
    assert(schema === Schema(
      StructType(Seq(
        StructField("_1", IntegerType, nullable = false),
        StructField("_2", NullType, nullable = true))),
      nullable = true))
  }

  test("SPARK-23835: add null check to non-nullable types in Tuples") {
    def numberOfCheckedArguments(deserializer: Expression): Int = {
      val newInstance = deserializer.collect { case n: NewInstance => n}.head
      newInstance.arguments.count(_.isInstanceOf[AssertNotNull])
    }
    assert(numberOfCheckedArguments(deserializerFor[(Double, Double)]) == 2)
    assert(numberOfCheckedArguments(deserializerFor[(java.lang.Double, Int)]) == 1)
    assert(numberOfCheckedArguments(deserializerFor[(java.lang.Integer, java.lang.Integer)]) == 0)
  }

  test("schema for case class that is a value class") {
    val schema = schemaFor[TestingValueClass.IntWrapper]
    assert(schema === Schema(IntegerType, nullable = false))
  }

  test("schema for case class that contains value class fields") {
    val schema = schemaFor[TestingValueClass.ValueClassData]
    assert(schema === Schema(
      StructType(Seq(
        StructField("intField", IntegerType, nullable = false),
        StructField("wrappedInt", IntegerType, nullable = false),
        StructField("strField", StringType, nullable = true),
        StructField("wrappedStr", StringType, nullable = true))),
      nullable = true))
  }
}
