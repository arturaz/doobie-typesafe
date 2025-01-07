# Table Definitions

You want to start by defining your SQL tables.

The following defines a `users` table with `id`, `first_name`, `last_name` and `nickname` columns:

```scala mdoc
import doobie.*

object Users extends TableDefinition("users") {
  val id: Column[Int] = Column("id")
  val firstName: Column[String] = Column("first_name")
  val lastName: Column[String] = Column("last_name")
  val nickname: Column[Option[String]] = Column("nickname")
}
```

## Composite Columns

You will often need to bundle several columns in the table as a Scala data structure. For example, you might want to
return a `Names` case class instead of a tuple of `String`s.

```scala mdoc
import doobie.*
import doobie.implicits.*

case class Names(firstName: String, lastName: String, nickname: Option[String])

lazy val names: SQLDefinition[Names] = Composite((
  Users.firstName.sqlDef, Users.lastName.sqlDef, Users.nickname.sqlDef
))(Names.apply)(Tuple.fromProductTyped)
```

Then we can query the composite column as follows:

```scala mdoc:nest
import doobie.*
import doobie.implicits.*

val id = 42
import Users as t
val columns = names
val querySql = sql"SELECT $columns FROM $t WHERE ${t.id === id}"
val query = querySql.queryOf(columns)
```

You can also use the `SQLDefinition` as another column in `Columns`:
```scala mdoc:nest
import doobie.*
import doobie.implicits.*

val id = 42
import Users as t
val columns = Columns((t.id, names))
val querySql = sql"SELECT $columns FROM $t WHERE ${t.id === id}"
val query = querySql.queryOf(columns)
```

Often you will want to define the `case class` in the `TableDefinition` object itself. There is a helper for that
use case:

```scala mdoc:reset
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
}

val id = 42
import Users as t
val columns = Columns((t.id, t.Names))
val querySql = sql"SELECT $columns FROM $t WHERE ${t.id === id}"
val query = querySql.queryOf(columns)
```

Ultimately, you will probably want to use a `case class` for the entire table row. You can do that as well:

```scala mdoc:reset
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

val id = 42
import Users as t
val columns = t.Row
val querySql = sql"SELECT $columns FROM $t WHERE ${t.id === id}"
val query = querySql.queryOf(columns)
```

The `TableDefinition.RowHelpers` trait provides a couple of helpers, such as:
```scala mdoc
Users.Row.insert
Users.Row.insertOnConflictDoNothing(Columns(Users.Names))
Users.Row.insertOnConflictDoNothing0
Users.Row.selectAll
```

Check their source code for the documentation.