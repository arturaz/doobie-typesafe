package doobie

import cats.Reducible
import doobie.util.pos.Pos


/**
 * @see [[TableName.insertInto]]
 * @note Eventually this will be deprecated in favor of a method on [[TableName]]
 **/
def insertInto[F[_]](table: TableName, columns: F[(Fragment, Fragment)])(using Reducible[F], Pos): Fragment =
  table.insertInto(columns)

/**
 * @see [[TableName.updateTable]]
 * @note Eventually this will be deprecated in favor of a method on [[TableName]]
 **/
def updateTable[F[_]](table: TableName, columns: F[(Fragment, Fragment)])(using Reducible[F], Pos): Fragment =
  table.updateTable(columns)

/**
 * Overload of [[updateTable]] for convenience.
 *
 * @note Eventually this will be deprecated in favor of a method on [[TableName]]
 */
def updateTable(table: TableName, column1: (Fragment, Fragment), other: (Fragment, Fragment)*)(using Pos): Fragment =
  table.updateTable(column1, other*)