# Updating

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

Update is very similar to insert:

```scala mdoc
import cats.data.NonEmptyVector
import doobie.*
import doobie.implicits.*


sql"""
${Users.updateTable(
  Users.firstName --> "John",
  Users.lastName --> "Doe"
)} WHERE ${Users.id === 42}
"""

// Or using a collection:
sql"""
${Users.updateTable(NonEmptyVector.of(
  Users.firstName --> "John",
  Users.lastName --> "Doe"
))} WHERE ${Users.id === 42}
"""
```

You can use the composite columns as well:

```scala mdoc
import cats.data.NonEmptyVector
import doobie.*
import doobie.implicits.*

sql"""
${Users.updateTable(Users.Names ==> Users.Names("John", "Doe", Some("Fast Johnny")))}
WHERE ${Users.id === 42}
"""
```