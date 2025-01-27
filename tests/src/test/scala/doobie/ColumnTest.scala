package doobie

import doobie.implicits.*
import munit.*

class ColumnTest extends CatsEffectSuite {
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
}
