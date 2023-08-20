package doobie

import doobie.implicits.*
import doobie.syntax.SqlInterpolator.SingleFragment

/**
 * Allows you to select columns in a type-safe way.
 *
 * Example:
 * {{{
 *   val columns = Columns((nameCol.tmf, ageCol.tmf, pet1Col.tmf, pet2Col.tmf))
 *   val resultQuery: Query0[(String, Int, String, Option[String])] =
 *     sql"SELECT $columns FROM $personWithPetsTable".queryOf(columns)
 * }}}
 *
 * @param sql returns the names of the columns, for example "name, surname, age".
 * @tparam QueryResult the type of `Fragment.queryOf(columns)` expression.
 */
case class Columns[+QueryResult] private (sql: Fragment) {
  def this(tuple: Tuple) = this(
    tuple.productIterator.map(_.asInstanceOf[TypedMultiFragment[?]].fragment).mkFragments(fr",")
  )
}
object Columns {
  given Conversion[Columns[?], Fragment] = _.sql
  given Conversion[Columns[?], SingleFragment[?]] = _.sql

  /**
   * Constructor for the generic case.
   *
   * Does not support subtyping (passing in a mix of [[TypedMultiFragment]] subtypes), use [[TypedMultiFragment.tmf]]
   * to convert.
   *
   * Not supported by all IDEs at the time of writing. For example, when using IntelliJ IDEA the type highlighting will
   * be broken due to [[https://youtrack.jetbrains.com/issue/SCL-21084 IntelliJ IDEA issues]].
   * */
  def apply[
    T <: Tuple : Tuple.IsMappedBy[TypedMultiFragment]
  ](t: T): Columns[Tuple.InverseMap[T, TypedMultiFragment]] = new Columns[Tuple.InverseMap[T, TypedMultiFragment]](t)

  /** Constructor for the single definition case. */
  def apply[A](sqlDef: TypedMultiFragment[A]): Columns[A] = new Columns[A](sqlDef.fragment)

  // Constructors for the specific cases, which provide support for subtyping
  // (passing in a mix of `TypedMultiFragment` subtypes) and better type highlighting in IntelliJ IDEA.

//region Generated `.apply`

  private type TF[A] = TypedMultiFragment[A]
  def apply[A1, A2](t: (TF[A1], TF[A2])): Columns[(A1, A2)] = new Columns[(A1, A2)](t)
  def apply[A1, A2, A3](t: (TF[A1], TF[A2], TF[A3])): Columns[(A1, A2, A3)] = new Columns[(A1, A2, A3)](t)
  def apply[A1, A2, A3, A4](t: (TF[A1], TF[A2], TF[A3], TF[A4])): Columns[(A1, A2, A3, A4)] = new Columns[(A1, A2, A3, A4)](t)
  def apply[A1, A2, A3, A4, A5](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5])): Columns[(A1, A2, A3, A4, A5)] = new Columns[(A1, A2, A3, A4, A5)](t)
  def apply[A1, A2, A3, A4, A5, A6](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6])): Columns[(A1, A2, A3, A4, A5, A6)] = new Columns[(A1, A2, A3, A4, A5, A6)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7])): Columns[(A1, A2, A3, A4, A5, A6, A7)] = new Columns[(A1, A2, A3, A4, A5, A6, A7)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13], TF[A14])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13], TF[A14], TF[A15])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13], TF[A14], TF[A15], TF[A16])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13], TF[A14], TF[A15], TF[A16], TF[A17])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13], TF[A14], TF[A15], TF[A16], TF[A17], TF[A18])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13], TF[A14], TF[A15], TF[A16], TF[A17], TF[A18], TF[A19])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13], TF[A14], TF[A15], TF[A16], TF[A17], TF[A18], TF[A19], TF[A20])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13], TF[A14], TF[A15], TF[A16], TF[A17], TF[A18], TF[A19], TF[A20], TF[A21])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21)](t)
  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22](t: (TF[A1], TF[A2], TF[A3], TF[A4], TF[A5], TF[A6], TF[A7], TF[A8], TF[A9], TF[A10], TF[A11], TF[A12], TF[A13], TF[A14], TF[A15], TF[A16], TF[A17], TF[A18], TF[A19], TF[A20], TF[A21], TF[A22])): Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22)] = new Columns[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22)](t)
//endregion
}