package doobie

import doobie.syntax.SqlInterpolator.SingleFragment

/**
 * A doobie fragment that potentially refers to a multiple columns/values (such as [[SQLDefinition]]) which produces a
 * value of type [[A]].
 *
 * Exists so that [[Columns]] could support [[Column]], [[SQLDefinition]], [[TypedFragment]] and [[TypedMultiFragment]].
 * */
trait TypedMultiFragment[+A] {
  def fragment: Fragment

  /**
   * To allow widening the type of [[Column]] and similar ones to [[TypedMultiFragment]].
   *
   * This is useful when working with tuples of [[TypedMultiFragment]]s, because `(Column[Int], Column[String])` is not the same
   * thing as `(TypedMultiFragment[Int], TypedMultiFragment[String])`.
   * */
  inline def tmf: TypedMultiFragment[A] = this
}
object TypedMultiFragment {
  given[A]: Conversion[Fragment, TypedMultiFragment[A]] = fr => new TypedMultiFragment[A] {
    def fragment = fr
  }

  given[A]: Conversion[TypedMultiFragment[A], Fragment] = _.fragment
  given[A]: Conversion[TypedMultiFragment[A], SingleFragment[A]] = _.fragment
}