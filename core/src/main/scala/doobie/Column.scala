package doobie

import cats.Reducible
import cats.data.NonEmptyVector
import doobie.implicits.*
import doobie.syntax.SqlInterpolator.SingleFragment

import scala.annotation.{targetName, unused}
import scala.util.NotGiven

/**
 * A definition of a single column in the database of type [[A]].
 *
 * Example:
 * {{{
 *   object SpecialWeaponStats extends TableDefinition("special_weapon_stats") {
 *     lazy val userId = Column[UserId]("user_id")
 *     lazy val weaponId = Column[WeaponGuid]("weapon_id")
 *     lazy val kills = Column[WeaponKills]("kills")
 *   }
 * }}}
 *
 * @param rawName the name of the column in the database
 * @param prefix the prefix of the table that this column belongs to, if specified. This is [[Some]] after you use
 *               [[prefixedWith]].
 * */
case class Column[A](
  rawName: String, prefix: Option[String] = None
)(
  using val read: Read[A], val write: Write[A]
) extends SQLDefinition[A] with TypedFragment[A] { self =>
  val name: Fragment = Fragment.const0(prefix.fold(rawName)(p => s"$p.$rawName"))

  override def fragment: Fragment = name

  override type Self[X] = Column[X]

  /** Returns the [[Get]] that is backing the [[read]]. */
  lazy val get: Get[A] = {
    // This is somewhat unsafe, but given that a column is a single column, it should be fine.
    read.gets.head._1.asInstanceOf[Get[A]]
  }

  /** Returns the [[Put]] that is backing the [[write]]. */
  lazy val put: Put[A] = {
    // This is somewhat unsafe, but given that a column is a single column, it should be fine.
    write.puts.head._1.asInstanceOf[Put[A]]
  }

  /** Creates an [[Option]] version of the [[Column]], giving that it is not already an [[Option]]. */
  def option[B](using @unused ng: NotGiven[A =:= Option[B]]): Column[Option[A]] =
    Column[Option[A]](rawName, prefix)(using
      read = Read.fromGetOption(self.get),
      write = Write.fromPutOption(self.put)
    )

  override def prefixedWith(prefix: String): Column[A] = Column[A](rawName = rawName, prefix = Some(prefix))

  override lazy val columns: NonEmptyVector[Column[?]] = NonEmptyVector.of(this)

  /** Returns a tuple of `(column_name, A)`. */
  inline def -->(a: A): (Fragment, Fragment) = (name, fr0"$a")

  /** Returns a tuple of `(column_name, A)`. */
  inline def -->(a: TypedFragment[A]): (Fragment, Fragment) = (name, a)
  
  /** For use in `UPDATE table_name SET name = ?` queries. */
  inline def setTo(a: A): Fragment = fr0"$name = $a"
  
  /** For use in `UPDATE table_name SET name = ?` queries. */
  inline def setTo(a: TypedFragment[A]): Fragment = fr0"$name = $a"

  @targetName("bindColumns")
  def ==>(a: A): NonEmptyVector[(Fragment, Fragment)] = NonEmptyVector.of(this --> a)

  /** Returns a `column_name = $a` [[Fragment]]. */
  @targetName("equals")
  def ===(a: A): TypedFragment[Boolean] = {
    // Ah, SQL is fun
    if (a.isInstanceOf[None.type]) fr0"$name IS NULL"
    else fr0"$name = $a"
  }

  /** Returns a `column_name = $a` [[Fragment]]. */
  def ===(a: Column[A]): TypedFragment[Boolean] = fr0"$name = $a"

  /** Returns a `column_name <> $a` [[Fragment]]. */
  def !==(a: A): TypedFragment[Boolean] = fr0"$name <> $a"

  /** Returns a `column_name <> $a` [[Fragment]]. */
  def !==(a: Column[A]): TypedFragment[Boolean] = fr0"$name <> $a"

  def <(a: A): TypedFragment[Boolean] = fr0"$name < $a"
  def <(a: TypedFragment[A]): TypedFragment[Boolean] = fr0"$name < $a"

  def <=(a: A): TypedFragment[Boolean] = fr0"$name <= $a"
  def <=(a: TypedFragment[A]): TypedFragment[Boolean] = fr0"$name <= $a"

  def >(a: A): TypedFragment[Boolean] = fr0"$name > $a"
  def >(a: TypedFragment[A]): TypedFragment[Boolean] = fr0"$name > $a"

  def >=(a: A): TypedFragment[Boolean] = fr0"$name >= $a"
  def >=(a: TypedFragment[A]): TypedFragment[Boolean] = fr0"$name >= $a"

  def +(a: A): TypedFragment[A] = fr0"$name + $a"
  def +(a: TypedFragment[A]): TypedFragment[A] = fr0"$name + $a"

  def -(a: A): TypedFragment[A] = fr0"$name - $a"
  def -(a: TypedFragment[A]): TypedFragment[A] = fr0"$name - $a"

  /** Returns a `column_name IN ($values)` [[Fragment]]. */
  def in[F[_]](values: F[A])(using Reducible[F], Write[A]): TypedFragment[Boolean] = {
    val valuesFragment = values.mapIntercalate(fr0"", fr0",")((acc, a) => fr0"$acc$a")
    fr0"$name IN ($valuesFragment)"
  }

  /** Returns `column_name IN (fs0, fs1, ...)` if there were elements or `FALSE` otherwise. */
  def in(values: IterableOnce[A])(using Write[A]): TypedFragment[Boolean] = {
    val result = values.iterator.mkFragmentsDetailed(fr0",")

    if (result.hadElements) fr0"$name IN (${result.fragment})" else fr0"FALSE"
  }

  /** Returns a `column_name NOT IN ($values)` [[Fragment]]. */
  def notIn[F[_]](values: F[A])(using Reducible[F], Write[A]): TypedFragment[Boolean] = {
    val valuesFragment = values.mapIntercalate(fr0"", fr0",")((acc, a) => fr0"$acc$a")
    fr0"$name NOT IN ($valuesFragment)"
  }

  /** Returns `column_name NOT IN (fs0, fs1, ...)` if there were elements or `TRUE` otherwise. */
  def notIn(values: IterableOnce[A])(using Write[A]): TypedFragment[Boolean] = {
    val result = values.iterator.mkFragmentsDetailed(fr0",")

    if (result.hadElements) fr0"$name NOT IN (${result.fragment})" else fr0"TRUE"
  }
}
object Column {
  given [A]: Conversion[Column[A], SQLDefinition[A]] = identity
  given [A]: Conversion[Column[A], Fragment] = _.sql
  given [A]: Conversion[Column[A], SingleFragment[A]] = c => SingleFragment(c.sql)

  extension [A] (c: Column[Option[A]]) {
    def isNull: TypedFragment[Boolean] = fr0"${c.name} IS NULL"
    def isNotNull: TypedFragment[Boolean] = fr0"${c.name} IS NOT NULL"

    //noinspection MutatorLikeMethodIsParameterless
    def setToNull: Fragment = fr0"${c.name} = NULL"
  }
}