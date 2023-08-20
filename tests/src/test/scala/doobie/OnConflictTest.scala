package doobie

import munit.CatsEffectSuite
import doobie.implicits.*

class OnConflictTest extends CatsEffectSuite {
  test("SQL generation") {
    val t = Person as "t"
    val actual = sql"${
      insertInto(t, t(_.nameCol) ==> "Steve")
    } ON CONFLICT (${t(_.nameCol)}) DO UPDATE SET ${t(_.ageCol)} = ${t(_.ageCol)} + ${t(_.ageCol).excluded}"
    val expected =
      sql"INSERT INTO person AS t (t.name) VALUES (?) ON CONFLICT (t.name) DO UPDATE SET t.age = t.age + EXCLUDED.age"
    assertEquals(actual.toString, expected.toString)
  }
}
