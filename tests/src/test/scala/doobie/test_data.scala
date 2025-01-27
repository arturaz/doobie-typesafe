package doobie

import cats.effect.Sync

given Meta[Age] = Meta[Int].timap(Age.apply)(_.age)
case class Age(age: Int) extends AnyVal

case class Person(name: String, age: Age)
object Person extends TableDefinition("person") {
  val nameCol = Column[String]("name")
  val ageCol = Column[Age]("age")
}
def nameCol = Person.nameCol
def ageCol = Person.ageCol
lazy val person: SQLDefinition[Person] = Composite(
  (nameCol.sqlDef, ageCol.sqlDef)
)(Person.apply)(Tuple.fromProductTyped)

object Pets extends TableDefinition("pets") {
  val pet1Col = Column[String]("pet1")
  val pet2Col = Column[Option[String]]("pet2")

  case class Row(pet1: String, pet2: Option[String])
  object Row
      extends WithSQLDefinition[Row](
        Composite((pet1Col.sqlDef, pet2Col.sqlDef))(Row.apply)(
          Tuple.fromProductTyped
        )
      )

  case class InvertedRow(pet2: Option[String], pet1: String)
  object InvertedRow
      extends WithSQLDefinition[InvertedRow](
        Composite((pet2Col.sqlDef, pet1Col.sqlDef))(InvertedRow.apply)(
          Tuple.fromProductTyped
        )
      )
}
def pet1Col = Pets.pet1Col
def pet2Col = Pets.pet2Col

object Cars extends TableDefinition("cars") {
  val idCol = Column[Long]("id")
  val makeCol = Column[String]("make")

  case class Row(id: Long, make: String)
  object Row
      extends WithSQLDefinition[Row](
        Composite((idCol.sqlDef, makeCol.sqlDef))(Row.apply)(
          Tuple.fromProductTyped
        )
      )
      with TableDefinition.RowHelpers[Row](this)

  case class RowWithoutAutogenerated(make: String)
  object RowWithoutAutogenerated
      extends WithSQLDefinition[RowWithoutAutogenerated](
        Composite(makeCol.sqlDef)(RowWithoutAutogenerated.apply)(_.make)
      )
      with TableDefinition.RowHelpers[RowWithoutAutogenerated](Cars)
}

given logHandler[M[_]: Sync]: LogHandler[M] = LogHandler.jdkLogHandler
