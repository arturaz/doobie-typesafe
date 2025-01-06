package doobie

import cats.data.NonEmptyVector
import doobie.util.{DBFixture, Helpers}
import implicits.*
import munit.CatsEffectSuite

class CompositeTest extends CatsEffectSuite with DBFixture with Helpers {
  test("sql") {
    assertEquals(person.sql.toString, fr0"name, age".toString)
  }

  test("==>") {
    val actual = person ==> Person("Alice", Age(42))
    val expected = NonEmptyVector.of(nameCol --> "Alice", ageCol --> Age(42))
    assertEquals(actual.map(_.toString()), expected.map(_.toString()))
  }

  test("===") {
    val actual = person === Person("Alice", Age(42))
    val expected = sql"(name = ? AND age = ?)"
    assertEquals(actual.fragment.toString, expected.toString)
  }

  test("columns") {
    assertEquals(person.columns, NonEmptyVector.of(nameCol, ageCol))
  }

  test("columnsSql") {
    assertEquals(person.columnsSql.toString, fr0"$nameCol, $ageCol".toString)
  }

  test("prefixedWith") {
    val p = Person `as` "p"
    val expected = NonEmptyVector.of(p.c(_.nameCol) --> "Alice", p.c(_.ageCol) --> Age(42))
    val actual = person.prefixedWith("p") ==> Person("Alice", Age(42))
    assertEquals(actual.map(_.toString()), expected.map(_.toString()))
  }

  test("prefixedWith in SQL") {
    val p = Person `as` "p"
    val expected = sql"SELECT p.name, p.age FROM person AS p".toString
    val actual = sql"SELECT ${p(_.nameCol)}, ${p(_.ageCol)} FROM $p".toString
    assertEquals(actual, expected)
  }

  val withTable = db.mapAsync { xa =>
    val create = sql"create table $Person ($nameCol text not null, $ageCol int not null)".update.run
    create.transact(xa).void.map(_ => xa).unsafeToFuture()
  }

  withTable.test("select") { xa =>
    val expected = Person("Alice", Age(42))
    val sql = for {
      _ <- insertInto(Person, person ==> expected).update.run
      result <- sql"select $person from $Person".queryOf(person).unique
    } yield result

    val actual = sql.transact(xa)
    actual.assertEquals(expected)
  }

  withTable.test("batch insert") { xa =>
    val expected = List(Person("Alice", Age(42)), Person("Bob", Age(43)))

    val sql = for {
      _ <-
        Update[Person](sql"INSERT INTO $Person (${person.columnsSql}) VALUES (${person.valuesSql})".rawSql)
        .updateMany(expected)
      results <- sql"select $person from $Person".queryOf(person).to[List]
    } yield results

    val actual = sql.transact(xa)
    actual.assertEquals(expected)
  }
}

class NestedCompositeTest extends CatsEffectSuite with DBFixture with Helpers {
  case class PersonWithPets(person: Person, pets: Pets)
  val personWithPets: SQLDefinition[PersonWithPets] =
    Composite((person, pets))(PersonWithPets.apply)(Tuple.fromProductTyped)
  val personWithPetsTable = new TableDefinition("person_with_pets") {}

  test("sql") {
    assertEquals(personWithPets.sql.toString, fr0"name, age, pet1, pet2".toString)
  }

  test("==> #1") {
    val actual = personWithPets ==> PersonWithPets(Person("Alice", Age(42)), Pets("Fido", Some("Spot")))
    val expected = NonEmptyVector.of(nameCol --> "Alice", ageCol --> Age(42), pet1Col --> "Fido", pet2Col --> Some("Spot"))
    assertEquals(actual.map(_.toString()), expected.map(_.toString()))
  }

  test("==> #2") {
    val actual = personWithPets ==> PersonWithPets(Person("Alice", Age(42)), Pets("Fido", None))
    val expected = NonEmptyVector.of(nameCol --> "Alice", ageCol --> Age(42), pet1Col --> "Fido", pet2Col --> None)
    assertEquals(actual.map(_.toString()), expected.map(_.toString()))
  }

  test("columns") {
    assertEquals(personWithPets.columns, NonEmptyVector.of(nameCol, ageCol, pet1Col, pet2Col))
  }

  test("columnsSql") {
    assertEquals(personWithPets.columnsSql.toString, fr0"$nameCol, $ageCol, $pet1Col, $pet2Col".toString)
  }

  val withTable = db.mapAsync { xa =>
    val create = sql"""
      create table $personWithPetsTable
      ($nameCol text not null, $ageCol int not null, $pet1Col text not null, $pet2Col text)
    """.update.run
    create.transact(xa).void.map(_ => xa).unsafeToFuture()
  }

  withTable.test("select") { xa =>
    val expected = PersonWithPets(Person("Alice", Age(42)), Pets("Fido", None))
    val sql = for {
      _ <- insertInto(personWithPetsTable, personWithPets ==> expected).update.run
      result <- sql"select $personWithPets from $personWithPetsTable".queryOf(personWithPets).unique
    } yield result

    val actual = sql.transact(xa)
    actual.assertEquals(expected)
  }

  withTable.test("select columns") { xa =>
    val expected = PersonWithPets(Person("Alice", Age(42)), Pets("Fido", None))
    val sql = for {
      _ <- insertInto(personWithPetsTable, personWithPets ==> expected).update.run
      columns = Columns((nameCol.sqlDef, ageCol.sqlDef, pet1Col.sqlDef, pet2Col.sqlDef))
      result <- sql"select $columns from $personWithPetsTable".queryOf(columns).unique
    } yield result

    val actual = sql.transact(xa)
    actual.assertEquals((expected.person.name, expected.person.age, expected.pets.pet1, expected.pets.pet2))
  }

  case class PersonWithPetsWrapper(personWithPets: PersonWithPets)
  object PersonWithPetsWrapper extends WithSQLDefinition[PersonWithPetsWrapper](
    personWithPets.imap(PersonWithPetsWrapper(_))(_.personWithPets)
  )

  withTable.test("select wrapped") { xa =>
    val expected = PersonWithPetsWrapper(PersonWithPets(Person("Alice", Age(42)), Pets("Fido", None)))

    val sql = for {
      _ <- insertInto(personWithPetsTable, personWithPets ==> expected.personWithPets).update.run
      result <- sql"select $PersonWithPetsWrapper from $personWithPetsTable".queryOf(PersonWithPetsWrapper).unique
    } yield result

    val actual = sql.transact(xa)
    actual.assertEquals(expected)
  }
}