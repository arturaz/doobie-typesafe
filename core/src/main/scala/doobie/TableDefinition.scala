package doobie

import cats.Reducible
import cats.data.NonEmptyVector
import doobie.implicits.*
import doobie.syntax.SqlInterpolator.SingleFragment
import doobie.util.pos.Pos

/** Defines a table.
  *
  * Example:
  * {{{
  *   object Users extends TableDefinition("users") {
  *     val id = Column[Int]("id")
  *   }
  * }}}
  *
  * @param rawName
  *   the name in database of the table.
  */
class TableDefinition(val rawName: String) extends TableName {
  val name: Fragment = Fragment.const0(rawName)

  override def toString = s"TableDefinition($rawName)"

  /** Allows you to give an alias for the table, useful in SQL joins.
    *
    * Example:
    * {{{
    *   val u = tables.Users as "u"
    *   val ic = tables.InventoryCharacters as "ic"
    *
    *   val columns = MatchmakingUserRowData
    *   sql"""
    *     SELECT $columns
    *     FROM $u
    *     INNER JOIN $ic ON ${u(_.id) === ic(_.userId)} AND ${u(_.character) === ic(_.guid)}
    *     LIMIT 1
    *   """.queryOf(columns)
    * }}}
    */
  def as(alias: String): AliasedTableDefinition[this.type] =
    AliasedTableDefinition(sql"$name AS ${Fragment.const0(alias)}", alias, this)
}
object TableDefinition {
  given Conversion[TableDefinition, Fragment] = _.name
  given Conversion[TableDefinition, SingleFragment[Nothing]] = t =>
    SingleFragment(t.name)

  /** Helper that allows you to quickly get some of the queries for the table.
    *
    * You should put this on the companion object of the row.
    *
    * Example:
    * {{{
    *   object Cars extends TableDefinition("cars") {
    *     val idCol = Column[Long]("id")
    *     val makeCol = Column[String]("make")
    *
    *     case class Row(id: Long, make: String)
    *     object Row extends WithSQLDefinition[Row](
    *       Composite((idCol.sqlDef, makeCol.sqlDef))(Row.apply)(Tuple.fromProductTyped)
    *     ) with TableDefinition.RowHelpers[Row](this)
    *   }
    * }}}
    *
    * @tparam TRow
    *   the type of the row.
    */
  trait RowHelpers[TRow](val tableDefinition: TableDefinition) {
    self: SQLDefinition[TRow] =>

    /** Inserts the whole row. */
    lazy val insert: Update[TRow] = {
      Update[TRow](insertSqlFor(tableDefinition).rawSql)
    }

    /** Inserts the whole row, ignoring if such row already exists. */
    lazy val insertOnConflictDoNothing0: Update[TRow] = {
      Update[TRow](
        sql"${insertSqlFor(tableDefinition)} ON CONFLICT DO NOTHING".rawSql
      )
    }

    /** Selects all rows. */
    lazy val selectAll: Query0[TRow] = {
      sql"SELECT $this FROM $tableDefinition".queryOf(this)
    }
  }

  extension [TRow](withDefinedRow: SQLDefinition[TRow] & RowHelpers[TRow]) {

    /** As [[RowHelpers.insertOnConflictDoNothing0]] but allows to specify the
      * conflict resolution columns.
      */
    def insertOnConflictDoNothing[A](columns: Columns[A]): Update[TRow] = {
      given Write[TRow] = withDefinedRow.write
      val insertSql =
        withDefinedRow.insertSqlFor(withDefinedRow.tableDefinition)
      Update[TRow](sql"$insertSql ON CONFLICT ($columns) DO NOTHING".rawSql)
    }
  }
}

/** Result of [[TableDefinition.as]].
  *
  * @param name
  *   the name of the table with the alias, for example "users AS u".
  * @param alias
  *   the alias of the table, for example "u".
  * @param original
  *   the original [[TableDefinition]].
  */
case class AliasedTableDefinition[T <: TableDefinition](
    name: Fragment,
    alias: String,
    original: T
) extends TableName {

  /** Shorthand for [[original]]. */
  inline def o: T = original

  /** [[alias]] as a [[Fragment]]. */
  def aliasFr: Fragment = Fragment.const0(alias)

  /** Prevents reinitialization of aliased [[SQLDefinition]]s over successive
    * [[prefix]] invocations.
    */
  private val aliasedDefinitionsCache =
    collection.concurrent.TrieMap.empty[SQLDefinition[?], SQLDefinition[?]]

  /** Allows you to prefix column names with the alias in the SQL definitions.
    *
    * `c` stands for `column`.
    *
    * Example:
    * {{{
    *   val u = tables.Users as "u"
    *   u.c(_.id).name == sql"u.${u.original.id.name}"
    * }}}
    */
  def c[A](f: T => Column[A]): Column[A] = prefix(f).asInstanceOf[Column[A]]

  /** Allows you to prefix column names with the alias in the SQL definitions.
    *
    * Example:
    * {{{
    *   val u = tables.Users as "u"
    *   u.c(_.complexSqlDefinition).sql == sql"u.name, u.surname, u.age"
    * }}}
    */
  // We have to specify two methods, because if we do this:
  //   def apply[A, Def[X] <: SQLDefinition[X]](f: T => Def[A]): Def[A]
  // The compiler freaks out in the use site with type error when you use this in sql interpolation, at least on 3.2.2.
  //   Found:    (_$7.nameCol : doobie.Column[String])
  //   Required: doobie.SQLDefinition[A] & doobie.syntax.SqlInterpolator.SingleFragment[A]
  //
  //   where:    A is a type variable with constraint
  def apply[A](f: T => SQLDefinition[A]): SQLDefinition[A] = prefix(f)

  private def prefix[A](f: T => SQLDefinition[A]): SQLDefinition[A] = {
    val picked = f(original)
    aliasedDefinitionsCache
      .getOrElseUpdate(picked, picked.prefixedWith(alias))
      .asInstanceOf[SQLDefinition[A]]
  }
}

sealed trait TableName {
  def name: Fragment

  /** Generates the SQL for `INSERT INTO table (column1, column2, ...) VALUES
    * (value1, value2, ...)`.
    *
    * Example:
    * {{{
    *   tables.InventoryCharacters.insertInto(NonEmptyVector.of(
    *     tables.InventoryCharacters.userId --> c.userId,
    *     tables.InventoryCharacters.guid --> c.guid
    *   ))
    * }}}
    */
  def insertInto[F[_]](
      columns: F[(Fragment, Fragment)]
  )(using Reducible[F], Pos): Fragment = {
    val columnNames = columns.mapIntercalate(fr0"", fr0", ") {
      case (acc, (name, _)) =>
        fr0"$acc$name"
    }
    val values = columns.mapIntercalate(fr0"", fr0", ") {
      case (acc, (_, value)) =>
        fr0"$acc$value"
    }
    sql"INSERT INTO $name ($columnNames) VALUES ($values)"
  }

  /** Generates the SQL for `UPDATE table SET column1 = value1, column2 =
    * value2, ...`.
    *
    * Example:
    * {{{
    *   tables.InventoryCharacters.updateTable(NonEmpty.createVector(
    *     tables.InventoryCharacters.handgun1 --> loadouts.sets.set1.weapons.handgun,
    *     tables.InventoryCharacters.handgun2 --> loadouts.sets.set2.weapons.handgun
    *   ))
    * }}}
    */
  def updateTable[F[_]](
      columns: F[(Fragment, Fragment)]
  )(using Reducible[F], Pos): Fragment = {
    val setClause = columns.mapIntercalate(fr0"", fr0", ") {
      case (acc, (name, value)) =>
        fr0"$acc$name = $value"
    }
    sql"UPDATE $name SET $setClause"
  }

  /** Overload of [[updateTable]] for convenience.
    */
  def updateTable(column1: (Fragment, Fragment), other: (Fragment, Fragment)*)(
      using Pos
  ): Fragment =
    updateTable(NonEmptyVector.of(column1, other*))
}
object TableName {
  given Conversion[TableName, Fragment] = _.name
  given Conversion[TableName, SingleFragment[Nothing]] = t =>
    SingleFragment(t.name)
}
