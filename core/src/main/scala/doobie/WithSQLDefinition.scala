package doobie


import cats.data.NonEmptyVector

import scala.annotation.targetName

/**
 * Allows you to inline a [[SQLDefinition]].
 *
 * This is useful when you want to expose the [[Read]] and [[Write]] of the [[A]] in the companion object.
 *
 * Example:
 * {{{
 *   case class Row(matchId: MatchId, userData: UserData)
 *   object Row extends WithSQLDefinition[Row](Composite((
 *     matchId.sqlDef, UserData.sqlDef
 *   ))(Row.apply)(Tuple.fromProductTyped))
 * }}}
 * */
trait WithSQLDefinition[A](val sqlDefinition: SQLDefinition[A]) extends SQLDefinition[A] {
  override def read: Read[A] = sqlDefinition.read
  override def write: Write[A] = sqlDefinition.write
  override def imap[B](mapper: A => B)(contramapper: B => A): Self[B] = 
    sqlDefinition.imap(mapper)(contramapper)
  @targetName("bindColumns")
  override def ==>(value: A): NonEmptyVector[(Fragment, Fragment)] = sqlDefinition ==> value
  @targetName("equals")
  override def ===(value: A): TypedFragment[Boolean] = sqlDefinition === value
  override def columns: NonEmptyVector[Column[?]] = sqlDefinition.columns
  override type Self[X] = sqlDefinition.Self[X]
  override def prefixedWith(prefix: String): Self[A] = sqlDefinition.prefixedWith(prefix)
}
