package doobie

import cats.Invariant
import cats.data.NonEmptyVector
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.syntax.SqlInterpolator.SingleFragment

import scala.annotation.targetName
import scala.annotation.unused
import scala.util.NotGiven

/** The read-only side of [[SQLDefinition]]. */
trait SQLDefinitionRead[A] extends TypedMultiFragment.Prefixable[A] { self =>
  type Self[X] <: SQLDefinitionRead[X]

  /** SQL that lists all of the columns to get this SQL result. */
  lazy val sql: Fragment = columns.map(_.name).intercalate(fr",")

  override def fragment: Fragment = sql

  /** Vector of columns */
  def columns: NonEmptyVector[Column[?]]

  /** Was this [[SQLDefinition]] created from a [[option]] method. */
  def isOption: Boolean

  given Read[A] = read

  /** Changes the type of the definition. The change cannot fail. Useful for wrapper types. */
  def map[B](mapper: A => B): SQLDefinitionRead[B] = SQLDefinitionRead.Mapped(this, mapper)
}
object SQLDefinitionRead {
  private class Mapped[A, B](val self: SQLDefinitionRead[A], val mapper: A => B) extends SQLDefinitionRead[B] {
    override type Self[X] = SQLDefinitionRead[X]

    override def columns: NonEmptyVector[Column[?]] = self.columns

    override def isOption: Boolean = self.isOption

    override def prefixedWith(prefix: String): SQLDefinitionRead[B] = Mapped(self.prefixedWith(prefix), mapper)

    override def fragment: Fragment = self.fragment

    override val read: Read[B] = self.read.map(mapper)
  }
}

/** An SQL definition that produces a value of type [[A]].
  *
  * The definition can be either:
  *   - A single [[Column]].
  *   - A [[Composite]] of multiple [[Column]]s.
  */
trait SQLDefinition[A] extends SQLDefinitionRead[A] { self =>
  type Self[X] <: SQLDefinition[X]

  /** Used in dependent type expressions. */
  type Result = A

  /** To allow widening the type of [[Column]] and similar ones to
    * [[SQLDefinition]].
    *
    * This is useful when working with tuples of [[SQLDefinition]]s, because
    * `(Column[Int], Column[String])` is not the same thing as `(SQLDefinition[Int], SQLDefinition[String])`.
    */
  inline def sqlDef: SQLDefinition[A] = this

  /** The SQL [[Fragment]] containing all of the [[Column.name]] joined with
   * ",".
   *
   * Useful in preparing batch inserts.
   * {{{
   *   Update[Person](
   *     sql"INSERT INTO $personTable (${person.columnsSql}) VALUES (${person.valuesSql})".rawSql
   *   ).updateMany(persons)
   * }}}
   */
//  lazy val columnsSql: Fragment = columns.map(_.sql).intercalate(fr",")

  def write: Write[A]

  given Write[A] = write

  /** Creates an [[Option]] version of the [[SQLDefinition]], giving that it is
   * not already an [[Option]].
   *
   * Useful when you are doing joins and want a non-nullable
   * [[Column]]/[[SQLDefinition]] to be represented as a nullable one.
   */
  def option[B](using @unused ng: NotGiven[A =:= Option[B]]): Self[Option[A]]

  /** Changes the type of the definition. The change must be invariant and
    * cannot fail. Useful for wrapper types.
    */
  def imap[B](mapper: A => B)(contramapper: B => A): Self[B]

  /** Converts the value into a vector of `(columnName, value)` pairs. */
  @targetName("bindColumns")
  def ==>(value: A): NonEmptyVector[(Fragment, Fragment)]

  /** Returns the SQL which evaluates to true if all columns of this
    * [[SQLDefinition]] are equal to the given value.
    */
  @targetName("equals")
  def ===(value: A): TypedFragment[Boolean]

  /** The SQL [[Fragment]] containing as many value placeholders (?) as there
    * are columns joined with ",".
    *
    * Useful in preparing batch inserts. See [[columnsSql]].
    */
  lazy val valuesSql: Fragment = columns.map(_ => fr0"?").intercalate(fr",")

  /** Combines [[columnsSql]] and [[valuesSql]].
    *
    * Useful in preparing batch inserts.
    * {{{
    *   Update[Person](
    *     sql"INSERT INTO $personTable ${person.insertSql}".rawSql
    *   ).updateMany(persons)
    * }}}
    */
  lazy val insertSql: Fragment = sql"($sql) VALUES ($valuesSql)"

  /** Full SQL for inserting into the given table.
    */
  def insertSqlFor(t: TableName): Fragment = sql"INSERT INTO $t $insertSql"

  /** Returns [[Update]] for batch inserts into the given table. */
  def batchInsertSqlFor(t: TableName): Update[A] =
    Update[A](insertSqlFor(t).rawSql)

  /** Generates a [[Fragment]] that sets all columns of the [[SQLDefinition]] to
    * their excluded values.
    */
  def setAllToExcluded: Fragment = {
    columns.map(column => sql"$column = ${column.excluded}").intercalate(fr",")
  }
}
object SQLDefinition {
  given write[A](using definition: SQLDefinition[A]): Write[A] = definition.write
  given toWrite[A]: Conversion[SQLDefinition[A], Write[A]] = _.write

  given invariant: Invariant[SQLDefinition] with {
    override def imap[A, B](fa: SQLDefinition[A])(f: A => B)(
        g: B => A
    ): SQLDefinition[B] = fa.imap(f)(g)
  }
}
