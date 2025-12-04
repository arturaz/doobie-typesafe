package doobie

import doobie.implicits.*
import doobie.util.{DBFixture, Helpers}
import munit.*

class ColumnTest extends CatsEffectSuite with DBFixture with Helpers {
  private val withTable = db.mapAsync { xa =>
    val create =
      sql"create table $Person (${Person.nameCol} text not null, ${Person.ageCol} int not null)".update.run
    create.transact(xa).void.map(_ => xa).unsafeToFuture()
  }

  test("instantiate from custom Meta[A]") {
    class Foo(val a: Int)
    given Meta[Foo] = Meta[Int].timap(a => new Foo(a))(_.a)

    // We test compilation. If this compiles, we're good.
    val _ = Column[Foo]("foo")
  }

  test("prefixedWith") {
    val c = Column[Int]("foo")
    assertEquals(c.prefixedWith("bar").name.toString, sql"bar.foo".toString)
  }

  test("prefixedWith twice") {
    val c = Column[Int]("foo")
    assertEquals(
      c.prefixedWith("unused").prefixedWith("bar").name.toString,
      sql"bar.foo".toString
    )
  }

  test("=== with Option") {
    val c = Column[Option[Int]]("foo")
    assertEquals((c === None).fragment.toString, sql"foo IS NULL".toString)
    assertEquals((c === Some(3)).fragment.toString, sql"foo = ?".toString)
  }

  withTable.test("imap") { xa =>
    val ageAsString = Person.ageCol.imap(_.age.toString)(s => Age(s.toInt))

    val table = Person
    val columns = Columns((Person.ageCol, ageAsString))
    val sql = for {
      _ <- table.insertInto(person ==> Person("Alice", Age(42))).update.run
      _ <- table.updateTable(ageAsString --> "100").update.run
      result <- sql"select $columns from $table".queryOf(columns).unique
    } yield result

    val actual = sql.transact(xa)
    actual.assertEquals((Age(100), "100"))
  }

  withTable.test("imap with option") { xa =>
    val ageAsString = Person.ageCol.imap(_.age.toString)(s => Age(s.toInt))

    val table = Person
    val columns = Columns((Person.ageCol, ageAsString.option))
    val sql = for {
      _ <- table.insertInto(person ==> Person("Alice", Age(42))).update.run
      _ <- table.updateTable(ageAsString --> "100").update.run
      result <- sql"select $columns from $table".queryOf(columns).unique
    } yield result

    val actual = sql.transact(xa)
    actual.assertEquals((Age(100), Some("100")))
  }
}
