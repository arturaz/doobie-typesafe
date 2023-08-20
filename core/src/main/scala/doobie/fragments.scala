package doobie

import cats.Reducible
import cats.data.NonEmptyVector
import doobie.implicits.*
import doobie.util.pos.Pos


/**
 * Generates the SQL for `INSERT INTO table (column1, column2, ...) VALUES (value1, value2, ...)`.
 *
 * Example:
 * {{{
 *   insertInto(tables.InventoryCharacters, NonEmptyVector.of(
 *     tables.InventoryCharacters.userId --> c.userId,
 *     tables.InventoryCharacters.guid --> c.guid
 *   ))
 * }}}
 */
def insertInto[F[_]](table: TableName, columns: F[(Fragment, Fragment)])(using Reducible[F], Pos): Fragment = {
  val columnNames = columns.mapIntercalate(fr0"", fr0", ") { case (acc, (name, _)) => 
    fr0"$acc$name"
  }
  val values = columns.mapIntercalate(fr0"", fr0", ") { case (acc, (_, value)) =>
    fr0"$acc$value"
  }
  sql"INSERT INTO $table ($columnNames) VALUES ($values)"
}

/**
 * Generates the SQL for `UPDATE table SET column1 = value1, column2 = value2, ...`.
 *
 * Example:
 * {{{
 *   updateTable(tables.InventoryCharacters, NonEmpty.createVector(
 *     tables.InventoryCharacters.handgun1 --> loadouts.sets.set1.weapons.handgun,
 *     tables.InventoryCharacters.handgun2 --> loadouts.sets.set2.weapons.handgun
 *   ))
 * }}}
 */
def updateTable[F[_]](table: TableName, columns: F[(Fragment, Fragment)])(using Reducible[F], Pos): Fragment = {
  val setClause = columns.mapIntercalate(fr0"", fr0", ") { case (acc, (name, value)) =>
    fr0"$acc$name = $value"
  }
  sql"UPDATE $table SET $setClause"
}

/**
 * Overload of [[updateTable]] for convenience.
 */
def updateTable(table: TableName, column1: (Fragment, Fragment), other: (Fragment, Fragment)*)(using Pos): Fragment =
  updateTable(table, NonEmptyVector.of(column1, other:_*))