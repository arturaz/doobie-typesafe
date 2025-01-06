package doobie

import cats.Invariant
import cats.data.NonEmptyVector
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.syntax.SqlInterpolator.SingleFragment

import scala.annotation.targetName


/**
 * An SQL definition that produces a value of type [[A]].
 *
 * The definition can be either:
 *   - A single [[Column]].
 *   - A [[Composite]] of multiple [[Column]]s.
 */
trait SQLDefinition[A] extends TypedMultiFragment[A] { self =>
  type Self[X] <: SQLDefinition[X]

  /** Used in dependent type expressions. */
  type Result = A

  /**
   * To allow widening the type of [[Column]] and similar ones to [[SQLDefinition]].
   *
   * This is useful when working with tuples of [[SQLDefinition]]s, because `(Column[Int], Column[String])` is not the same
   * thing as `(SQLResult[Int], SQLResult[String])`.
   * */
  inline def sqlDef: SQLDefinition[A] = this

  /** SQL that lists all of the columns to get this SQL result. */
  lazy val sql: Fragment = columns.map(_.name).intercalate(fr",")

  def fragment: Fragment = sql

  /** Prefixes all column names with `prefix.`. If this already had a prefix, the prefix is replaced. */
  def prefixedWith(prefix: String): Self[A]

  /**
   * Prefixes all column names with `EXCLUDED`, which is a special SQL table name when resolving insert/update conflicts.
   *
   * Example: {{{
   *   sql"""
   *      ${t.Row.insertSqlFor(t)}
   *      ON CONFLICT (${t.userId}, ${t.weaponId}) DO UPDATE SET ${t.kills} = ${t.kills} + ${t.kills.excluded}
   *   """
   * }}}
   * */
  lazy val excluded: Self[A] = prefixedWith("EXCLUDED")

  def read: Read[A]
  given Read[A] = read

  def write: Write[A]
  given Write[A] = write

  /** Changes the type of the definition. The change must be invariant and cannot fail. Useful for wrapper types. */
  def imap[B](mapper: A => B)(contramapper: B => A): Self[B]

  /** Converts the value into a vector of `(columnName, value)` pairs. */
  @targetName("bindColumns")
  def ==>(value: A): NonEmptyVector[(Fragment, Fragment)]

  /** Returns the SQL which evaluates to true if all columns of this [[SQLDefinition]] are equal to the given value. */
  @targetName("equals")
  def ===(value: A): TypedFragment[Boolean]

  /** Vector of columns */
  def columns: NonEmptyVector[Column[?]]

  /**
   * The SQL [[Fragment]] containing all of the [[Column.name]] joined with ",".
   *
   * Useful in preparing batch inserts.
   * {{{
   *   Update[Person](
   *     sql"INSERT INTO $personTable (${person.columnsSql}) VALUES (${person.valuesSql})".rawSql
   *   ).updateMany(persons)
   * }}}
   * */
  lazy val columnsSql: Fragment = columns.map(_.sql).intercalate(fr",")

  /**
   * The SQL [[Fragment]] containing as many value placeholders (?) as there are columns joined with ",".
   *
   * Useful in preparing batch inserts. See [[columnsSql]].
   * */
  lazy val valuesSql: Fragment = columns.map(_ => fr0"?").intercalate(fr",")

  /**
   * Combines [[columnsSql]] and [[valuesSql]].
   *
   * Useful in preparing batch inserts.
   * {{{
   *   Update[Person](
   *     sql"INSERT INTO $personTable ${person.insertSql}".rawSql
   *   ).updateMany(persons)
   * }}}
   * */
  lazy val insertSql: Fragment = sql"($columnsSql) VALUES ($valuesSql)"

  /**
   * Full SQL for inserting into the given table.
   */
  def insertSqlFor(t: TableName): Fragment = sql"INSERT INTO $t $insertSql"

  /** Returns [[Update]] for batch inserts into the given table. */
  def batchInsertSqlFor(t: TableName): Update[A] = Update[A](insertSqlFor(t).rawSql)

  /** Generates a [[Fragment]] that sets all columns of the [[SQLDefinition]] to their excluded values. */
  def setAllToExcluded: Fragment = {
    columns.map(column => sql"$column = ${column.excluded}").intercalate(fr",")
  }
}
object SQLDefinition {
  given read[A](using definition: SQLDefinition[A]): Read[A] = definition.read
  given write[A](using definition: SQLDefinition[A]): Write[A] = definition.write
  given toRead[A]: Conversion[SQLDefinition[A], Read[A]] = _.read
  given toWrite[A]: Conversion[SQLDefinition[A], Write[A]] = _.write
  given toFragment[A]: Conversion[SQLDefinition[A], Fragment] = _.sql
  given toSingleFragment[A]: Conversion[SQLDefinition[A], SingleFragment[Nothing]] = d => SingleFragment(d.sql)

  given invariant: Invariant[SQLDefinition] with {
    override def imap[A, B](fa: SQLDefinition[A])(f: A => B)(g: B => A): SQLDefinition[B] = fa.imap(f)(g)
  }
}