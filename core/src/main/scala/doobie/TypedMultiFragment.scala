package doobie

import cats.data.NonEmptyVector
import doobie.syntax.SqlInterpolator.SingleFragment

/** A doobie fragment that potentially refers to a multiple columns/values (such
  * as [[SQLDefinition]]) which produces a value of type [[A]].
  *
  * Exists so that [[Columns]] could support [[Column]], [[SQLDefinition]],
  * [[TypedFragment]] and [[TypedMultiFragment]].
  */
trait TypedMultiFragment[A] {
  def fragment: Fragment

  def read: Read[A]

  /** To allow widening the type of [[Column]] and similar ones to
    * [[TypedMultiFragment]].
    *
    * This is useful when working with tuples of [[TypedMultiFragment]]s,
    * because `(Column[Int], Column[String])` is not the same thing as
    * `(TypedMultiFragment[Int], TypedMultiFragment[String])`.
    */
  inline def tmf: TypedMultiFragment[A] = this
}
object TypedMultiFragment {
  type TupleValues[T <: Tuple] = Tuple.InverseMap[T, TypedMultiFragment]

  given [A](using r: Read[A]): Conversion[Fragment, TypedMultiFragment[A]] =
    fr =>
      new TypedMultiFragment[A] {
        override def fragment = fr
        override def read = r
      }

  given toFragment[A]: Conversion[TypedMultiFragment[A], Fragment] = _.fragment
  given toSingleFragment[A]: Conversion[TypedMultiFragment[A], SingleFragment[A]] = _.fragment
  given read[A](using definition: TypedMultiFragment[A]): Read[A] = definition.read
  given toRead[A]: Conversion[TypedMultiFragment[A], Read[A]] = _.read

  /** A [[TypedMultiFragment]] that can be prefixed. */
  trait Prefixable[A] extends TypedMultiFragment[A] {
    type Self[X] <: Prefixable[X]

    /** Prefixes all column names with `prefix.`. If this already had a prefix,
     * the prefix is replaced.
     */
    def prefixedWith(prefix: String): Self[A]

    /** Prefixes all column names with `EXCLUDED`, which is a special SQL table
     * name when resolving insert/update conflicts.
     *
     * Example: {{{
     * sql"""
     *   ${t.Row.insertSqlFor(t)} ON CONFLICT (${t.userId},
     *   ${t.weaponId}) DO UPDATE SET ${t.kills} = ${t.kills} + ${t.kills.excluded}
     * """ }}}
     */
    lazy val excluded: Self[A] = prefixedWith("EXCLUDED")
  }

  /** Amount of results to skip from the [[ResultSet]] when reading the Nth
    * tuple element.
    */
  def sqlResultAccumulatedLengths(
      tmfs: Vector[TypedMultiFragment[?]]
  ): Vector[Int] =
    tmfs.scanLeft(0)(_ + _.read.length).init

  /** Constructs a [[Read]] for a bunch of [[TypedMultiFragment]]s. */
  def read[A](
      tmfs: NonEmptyVector[TypedMultiFragment[?]]
  )(map: Iterator[Any] => A): Read[A] =
    tmfs.tail
      .foldLeft(tmfs.head.read.map(_ :: Nil): Read[List[Any]]) {
        case (r, tmf) =>
          Read.Composite(r, tmf.read, (list, value) => value :: list)
      }
      .map(list => map(list.reverseIterator))
// Previous implementation:
//    new Read(
//      gets = tmfs.iterator.flatMap(_.read.gets).toList,
//      unsafeGet = (rs, idx) => {
//        val iterator = tmfs.iterator.zip(sqlResultAccumulatedLengths).map {
//          case (r, toSkip) =>
//            val position = idx + toSkip
//            try {
//              r.read.unsafeGet(rs, position)
//            } catch {
//              case NonFatal(e) =>
//                throw new Exception(
//                  s"Error while reading $r at position $position from a ResultSet",
//                  e
//                )
//            }
//        }
//
//        map(iterator)
//      }
//    )
}
