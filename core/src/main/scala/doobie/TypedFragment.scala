package doobie

import doobie.*
import doobie.implicits.*
import doobie.syntax.SqlInterpolator.SingleFragment

/** A doobie fragment that refers to a single column/value which produces a
  * value of type [[A]].
  */
trait TypedFragment[A] extends TypedMultiFragment[A]
object TypedFragment {
  extension [A](tf: TypedFragment[A]) {

    /** Allows you to use the [[TypedFragment]] in a SQL query.
      *
      * For example:
      * {{{
      *   val matchIdSql: TypedFragment[Option[MatchId]] =
      *     sql"($matchData->session_mode->>match_id)::uuid"
      *   sql"UPDATE matches SET ${matchIdSql === Some(matchId)}"
      * }}}
      */
    def ===(a: A)(using Write[A]): Fragment = sql"$tf = $a"
  }

  /** @param p
    *   the instance is required to construct the [[TypedFragment.fragment]]
    */
  given [A](using r: Read[A], p: Put[A]): Conversion[A, TypedFragment[A]] = a =>
    {
      new TypedFragment[A] {
        override val fragment = sql"$a"
        override def read = r
      }
    }
  given [A](using r: Read[A]): Conversion[Fragment, TypedFragment[A]] = fr =>
    new TypedFragment[A] {
      override def fragment = fr
      override def read = r
    }
  given [A]: Conversion[TypedFragment[A], Fragment] = _.fragment
  given [A]: Conversion[TypedFragment[A], SingleFragment[A]] = _.fragment
}
