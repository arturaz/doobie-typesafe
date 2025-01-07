# Querying

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

Main benefits of this library are:
- You can not mix up the order of the columns in the query and their types.
- Certain operations, like equality, set membership, comparisons can be done in type-safe way.

All the following examples have this code in common:
```scala mdoc
import doobie.*
import doobie.implicits.*

import Users as t
val columns = Columns((t.firstName, t.lastName))
```

## Equality

```scala mdoc:nest

val querySql = sql"SELECT $columns FROM $t WHERE ${t.id === 42}"
val query = querySql.queryOf(columns)

val notEqualQuerySql = sql"SELECT $columns FROM $t WHERE ${t.id !== 42}"
val notEqualQuery = querySql.queryOf(columns)
```

## Set Membership

```scala mdoc:nest
val multiQuerySql = sql"SELECT $columns FROM $t WHERE ${t.id in Vector(1, 2, 3)}"
val multiQuery = multiQuerySql.queryOf(columns)

val notInMultiQuerySql = sql"SELECT $columns FROM $t WHERE ${t.id notIn Vector(1, 2, 3)}"
val notInMultiQuery = multiQuerySql.queryOf(columns)
```

## Nullability

```scala mdoc:nest
val hasNoNicknameQuerySql = sql"SELECT $columns FROM $t WHERE ${t.nickname === None}"
val hasNoNicknameQuery = hasNoNicknameQuerySql.queryOf(columns)

val hasNicknameQuerySql = sql"SELECT $columns FROM $t WHERE ${t.nickname !== None}"
val hasNicknameQuery = hasNicknameQuerySql.queryOf(columns)

val hasSpecificNicknameQuerySql = sql"SELECT $columns FROM $t WHERE ${t.nickname === Some("Johnny")}"
val hasSpecificNicknameQuery = hasSpecificNicknameQuerySql.queryOf(columns)
```

Take note that:

- Column types are inferred from the column definitions.
- The returned query type is inferred from the columns used in the query.
- The operators are used to compare columns to values and they are typesafe.

## Performing Table Joins

Given that we have the following `addresses` table:

```scala mdoc
import doobie.*

object Addresses extends TableDefinition("addresses") {
  val id: Column[Int] = Column("id")
  val userId: Column[Int] = Column("user_id")
  val street: Column[String] = Column("street")
  val city: Column[String] = Column("city")
  
  case class Address(street: String, city: String)
  object Address extends WithSQLDefinition[Address](Composite((
    street.sqlDef, city.sqlDef
  ))(Address.apply)(Tuple.fromProductTyped))
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

### Left/Right Joins

When performing a left/right join one side, which was previously non-nullable, becomes nullable.

To deal with that, use `.option` when specifying your columns or SQLDefinitions.

```scala mdoc:nest
import doobie.*
import doobie.implicits.*

val id = 42

// Construct the prefixed versions of the tables. They will be known as `u` and `a` in the query.
val u = Users as "u"
val a = Addresses as "a"

val columns = Columns((u(_.firstName), u(_.lastName), a(_.street.option)))

val query1Sql =
  sql"SELECT $columns FROM $u LEFT JOIN $a ON ${u.c(_.id) === a.c(_.userId)} WHERE ${u.c(_.id) === id}"
val query1 = query1Sql.queryOf(columns)
```

## Related projects

You can use the [doobieroll assembler](https://jatcwang.github.io/doobieroll/docs/assembler) to turn relational data back to object hierarchies. 
