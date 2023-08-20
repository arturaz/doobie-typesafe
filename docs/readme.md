# Doobie Typesafe

A typesafe wrapper for [doobie](https://tpolecat.github.io/doobie/) that allows you to write queries in a typesafe way.

Goals of this library:
- Allow you to refer to SQL tables and columns in a typesafe way so that values of the wrong type cannot be used.

Non-goals:
- Provide a typesafe DSL for writing SQL queries. You still have to write the SQL yourself and validate it using 
  [doobie typechecking facilities](https://tpolecat.github.io/doobie/docs/06-Checking.html).

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "io.github.arturaz" % "doobie-typesafe" % "@VERSION@"
```

The library is only published for Scala 3 due to the use of 
[Scala 3 match types](https://docs.scala-lang.org/scala3/reference/new-types/match-types.html).

## Table Definitions

You want to start by defining your SQL tables.

The following defines a `users` table with `id` and `name` columns.

```scala mdoc
import doobie.*

object Users extends TableDefinition("users") {
  val id: Column[Int] = Column("id")
  val firstName: Column[String] = Column("first_name")
  val lastName: Column[String] = Column("last_name")
}
```

## Querying

```scala mdoc:nest
import doobie.implicits.*

val id = 42

import Users as t
val columns = Columns((t.firstName, t.lastName))
val querySql = sql"SELECT $columns FROM $t WHERE ${t.id === id}"
val query = querySql.queryOf(columns)
```

Take note that:
- Column types are inferred from the column definitions.
- The returned query type is inferred from the columns used in the query.
- The equality operator `===` is used to compare columns to values and is typesafe.

## Performing Table Joins

Given that we have the following `addresses` table:

```scala mdoc
object Addresses extends TableDefinition("addresses") {
  val id: Column[Int] = Column("id")
  val userId: Column[Int] = Column("user_id")
  val street: Column[String] = Column("street")
  val city: Column[String] = Column("city")
}
```

You can perform a table join as follows:

```scala mdoc:nest
import doobie.*
import doobie.implicits.*

val id = 42

// Construct the prefixed versions of the tables. They will be known as `u` and `a` in the query.
val u = Users as "u"
val a = Addresses as "a"

// Construct the prefixed versions of the columns to prevent name clashes between the tables.
// They will be known as `u.first_name`, `u.last_name`, `a.street` and `a.city` in the query.
val columns = Columns((u(_.firstName), u(_.lastName), a(_.street), a(_.city)))

// You can join the tables using the `===` operator as well. You have to use the `c` method to access the columns.
val query1Sql =
  sql"SELECT $columns FROM $u INNER JOIN $a ON ${u.c(_.id) === a.c(_.userId)} WHERE ${u.c(_.id) === id}"
val query1 = query1Sql.queryOf(columns)

// Alternatively, instead of writing `u.c(_.id)` you can use `u(_.id)`, however that is less typesafe, as `apply` 
// accepts any `SQLDefinition`, not just a `Column`.
val query2Sql =
  sql"SELECT $columns FROM $u INNER JOIN $a ON ${u(_.id)} = ${a(_.userId)} WHERE ${u.c(_.id) === id}"
val query2 = query2Sql.queryOf(columns)
```

## Composite Columns

You will often need to bundle several columns in the table as a Scala data structure. For example, you might want to
return a `Names` case class instead of a tuple of `String`s for the `getUserQuery` above.

```scala mdoc
import doobie.*
import doobie.implicits.*

case class Names(firstName: String, lastName: String)

lazy val names: SQLDefinition[Names] = Composite((
  Users.firstName.sqlDef, Users.lastName.sqlDef
))(Names.apply)(Tuple.fromProductTyped)
```

Then the `getUserQuery` can be rewritten as follows:

```scala mdoc:nest
val id = 42
import Users as t
val columns = names
val querySql = sql"SELECT $columns FROM $t WHERE ${t.id === id}"
val query = querySql.queryOf(columns)
```

You can also use the `SQLDefinition` as another column in `Columns`:
```scala mdoc:nest
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
  
  case class Names(firstName: String, lastName: String)
  object Names extends WithSQLDefinition[Names](Composite((
    firstName.sqlDef, lastName.sqlDef
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
  
  case class Names(firstName: String, lastName: String)
  object Names extends WithSQLDefinition[Names](Composite((
    firstName.sqlDef, lastName.sqlDef
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

The the source code for their documentation.

## Inserting

You can insert rows into the table by specifying the individual columns:

```scala mdoc
import cats.data.NonEmptyVector

insertInto(Users, NonEmptyVector.of(
  Users.id --> 42,
  Users.firstName --> "John",
  Users.lastName --> "Doe"
))
```

You can use the composite columns as well:

```scala mdoc
import cats.data.NonEmptyVector

insertInto(Users, NonEmptyVector.of(
  Users.id --> 42,
) ++: (Users.Names ==> Users.Names("John", "Doe")))
```

Or you can use the `Row` case class:

```scala mdoc
// Insert single row.
insertInto(Users, Users.Row ==> Users.Row(42, Users.Names("John", "Doe")))
Users.Row.insert.toUpdate0(Users.Row(42, Users.Names("John", "Doe")))

// Batch insert.
Users.Row.insert.updateMany(NonEmptyVector.of(
  Users.Row(42, Users.Names("John", "Doe")),
  Users.Row(43, Users.Names("Jane", "Doe"))
))
```

## Updating

Update is very similar to insert:

```scala mdoc
import cats.data.NonEmptyVector
import doobie.implicits.*


sql"""
${updateTable(Users,
  Users.firstName --> "John",
  Users.lastName --> "Doe"
)} WHERE ${Users.id === 42}
""".stripMargin

// Or using a collection:
sql"""
${updateTable(Users, NonEmptyVector.of(
  Users.firstName --> "John",
  Users.lastName --> "Doe"
))} WHERE ${Users.id === 42}
""".stripMargin
```

You can use the composite columns as well:

```scala mdoc
import cats.data.NonEmptyVector
import doobie.implicits.*

sql"""
${updateTable(Users, Users.Names ==> Users.Names("John", "Doe"))}
WHERE ${Users.id === 42}
""".stripMargin
```

## Final Notes

- Extra effort has been made to document each type and function in the library, so make sure to check out the source 
  code.
- The library is still in early stages of development, so breaking changes can occur.

## Credits

The author of this library is [Artūras Šlajus](https://arturaz.net).