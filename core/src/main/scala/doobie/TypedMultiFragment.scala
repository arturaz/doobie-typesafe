package doobie

import doobie.syntax.SqlInterpolator.SingleFragment

import scala.util.control.NonFatal

/**
 * A doobie fragment that potentially refers to a multiple columns/values (such as [[SQLDefinition]]) which produces a
 * value of type [[A]].
 *
 * Exists so that [[Columns]] could support [[Column]], [[SQLDefinition]], [[TypedFragment]] and [[TypedMultiFragment]].
 * */
trait TypedMultiFragment[A] {
  def fragment: Fragment

  def read: Read[A]

  /**
   * To allow widening the type of [[Column]] and similar ones to [[TypedMultiFragment]].
   *
   * This is useful when working with tuples of [[TypedMultiFragment]]s, because `(Column[Int], Column[String])` is
   * not the same thing as `(TypedMultiFragment[Int], TypedMultiFragment[String])`.
   * */
  inline def tmf: TypedMultiFragment[A] = this
}
object TypedMultiFragment {
  type TupleValues[T <: Tuple] = Tuple.InverseMap[T, TypedMultiFragment]

  given[A](using r: Read[A]): Conversion[Fragment, TypedMultiFragment[A]] = fr => new TypedMultiFragment[A] {
    override def fragment = fr
    override def read = r
  }

  given[A]: Conversion[TypedMultiFragment[A], Fragment] = _.fragment
  given[A]: Conversion[TypedMultiFragment[A], SingleFragment[A]] = _.fragment

  /** Amount of results to skip from the [[ResultSet]] when reading the Nth tuple element. */
  def sqlResultAccumulatedLengths(tmfs: Vector[TypedMultiFragment[?]]): Vector[Int] =
    tmfs.scanLeft(0)(_ + _.read.length).init

  /** Constructs a [[Read]] for a bunch of [[TypedMultiFragment]]s. */
  def read[A](
    tmfs: Vector[TypedMultiFragment[?]]
  )(map: Iterator[Any] => A): Read[A] =
    read(tmfs, sqlResultAccumulatedLengths(tmfs))(map)

  /** Constructs a [[Read]] for a bunch of [[TypedMultiFragment]]s. */
  def read[A](
    tmfs: Vector[TypedMultiFragment[?]], sqlResultAccumulatedLengths: Vector[Int]
  )(map: Iterator[Any] => A): Read[A] = {
    Read(
      gets = tmfs.iterator.flatMap(_.read.gets).toList,
      unsafeGet = (rs, idx) => {
        val iterator = tmfs.iterator.zip(sqlResultAccumulatedLengths).map { case (r, toSkip) =>
          val position = idx + toSkip
          try {
            r.read.unsafeGet(rs, position)
          }
          catch {
            case NonFatal(e) =>
              throw new Exception(s"Error while reading $r at position $position from a ResultSet", e)
          }
        }

        map(iterator)
      }
    )
  }
}