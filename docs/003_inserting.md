# Inserting

```scala mdoc:invisible
import doobie.*
import doobie.implicits.*

object Users extends TableDefinition("users") {
  val id: Column[Int] = Column("id")
  val firstName: Column[String] = Column("first_name")
  val lastName: Column[String] = Column("last_name")
  val nickname: Column[Option[String]] = Column("nickname")

  case class Names(firstName: String, lastName: String, nickname: Option[String])
  object Names extends WithSQLDefinition[Names](Composite((
    firstName.sqlDef, lastName.sqlDef, nickname.sqlDef
  ))(Names.apply)(Tuple.fromProductTyped))
  
  case class Row(id: Int, names: Names)
  object Row extends WithSQLDefinition[Row](Composite((
    id.sqlDef, Names.sqlDef
  ))(Row.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[Row](this)
}
```

You can insert rows into the table by specifying the individual columns:

```scala mdoc
import cats.data.NonEmptyVector

Users.insertInto(NonEmptyVector.of(
  Users.id --> 42,
  Users.firstName --> "John",
  Users.lastName --> "Doe",
  Users.nickname --> None,
))
```

You can use the composite columns as well:

```scala mdoc
import cats.data.NonEmptyVector

Users.insertInto(NonEmptyVector.of(
  Users.id --> 42,
) ++: (Users.Names ==> Users.Names("John", "Doe", Some("Johnny"))))
```

Or you can use the `Row` case class:

```scala mdoc
// Insert single row.
Users.insertInto(Users.Row ==> Users.Row(42, Users.Names("John", "Doe", Some("Johnny"))))
Users.Row.insert.toUpdate0(Users.Row(42, Users.Names("John", "Doe", Some("Johnny"))))

// Batch insert.
Users.Row.insert.updateMany(NonEmptyVector.of(
  Users.Row(42, Users.Names("John", "Doe", Some("Johnny"))),
  Users.Row(43, Users.Names("Jane", "Doe", None))
))
```