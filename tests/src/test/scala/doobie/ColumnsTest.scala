package doobie

import doobie.implicits.*
import munit.*

class ColumnsTest extends CatsEffectSuite {
  test("sql") {
    val columns = Columns((nameCol.sqlDef, ageCol.sqlDef))
    assertEquals(columns.sql.toString, sql"name, age".toString)
  }

  test("sql with defs") {
    val ageAsString: TypedFragment[String] = sql"$ageCol::string"
    val columns = Columns((nameCol.sqlDef, ageAsString))
    assertEquals(columns.sql.toString, sql"name, age::string".toString)
  }

  test("queryOf") {
    val columns = Columns((nameCol.sqlDef, ageCol.sqlDef))
    assertEquals(
      sql"SELECT $columns FROM $Person".queryOf(columns).sql,
      "SELECT name, age FROM person"
    )
  }
}
