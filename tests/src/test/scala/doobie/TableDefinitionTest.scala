package doobie

import doobie.*
import doobie.implicits.*
import munit.FunSuite

class TableDefinitionTest extends FunSuite {
  test("conversion to fragment from fragment name") {
    val t = new TableDefinition("test") {}
    assertEquals(
      sql"SELECT * FROM $t".toString,
      sql"SELECT * FROM test".toString
    )
  }

  test("conversion to fragment from string name") {
    val t = new TableDefinition("test") {}
    assertEquals(
      sql"SELECT * FROM $t".toString,
      sql"SELECT * FROM test".toString
    )
  }
}
