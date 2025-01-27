package doobie

import cats.Foldable
import cats.Semigroup
import cats.syntax.foldable.*
import doobie.implicits.*
import fs2.Stream

extension [F[_], A](fa: F[A])(using Foldable[F]) {

  /** Example:
    * {{{
    *   List(1, 2, 3).mapIntercalate("Words: ", ", ")((str, num) => s"$str$num")
    *   // "Words: 1, 2, 3"
    * }}}
    *
    * @param starting
    *   the starting value
    * @param separator
    *   the values that go between the elements
    */
  def mapIntercalate[B](starting: B, separator: B)(
      f: (B, A) => B
  )(using semi: Semigroup[B]): B = {
    var first = true
    fa.foldLeft(starting) { (b, a) =>
      if (first) {
        first = false
        f(b, a)
      } else {
        f(semi.combine(b, separator), a)
      }
    }
  }
}

case class MkFragmentsResult(fragment: Fragment, hadElements: Boolean) {
  def getOrElse(fragment: Fragment): Fragment =
    if (hadElements) this.fragment else fragment
}

extension (iterator: Iterator[Fragment]) {
  def mkFragmentsDetailed(separator: Fragment): MkFragmentsResult = {
    var current = fr0""
    var first = true
    for (a <- iterator) {
      if (!first) current = current ++ separator
      current = current ++ a
      first = false
    }
    MkFragmentsResult(current, !first)
  }

  def mkFragments(separator: Fragment): Fragment =
    mkFragmentsDetailed(separator).fragment
}

extension [A](iterator: Iterator[A])(using Write[A]) {
  def mkFragmentsDetailed(separator: Fragment): MkFragmentsResult =
    iterator.map(a => fr0"$a").mkFragmentsDetailed(separator)

  def mkFragments(separator: Fragment): Fragment =
    mkFragmentsDetailed(separator).fragment
}

extension (iterable: IterableOnce[Fragment]) {
  def mkFragmentsDetailed(separator: Fragment): MkFragmentsResult =
    iterable.iterator.mkFragmentsDetailed(separator)

  def mkFragments(separator: Fragment): Fragment =
    mkFragmentsDetailed(separator).fragment
}

extension [A](iterable: IterableOnce[A])(using Write[A]) {
  def mkFragmentsDetailed(separator: Fragment): MkFragmentsResult =
    iterable.iterator.mkFragmentsDetailed(separator)

  def mkFragments(separator: Fragment): Fragment =
    mkFragmentsDetailed(separator).fragment
}

extension [A](update: Update[A]) {

  /** As [[doobie.util.update.Update.updateManyWithGeneratedKeys]] but typesafe.
    */
  def updateManyWithGeneratedKeys[K](
      sqlDef: SQLDefinition[K]
  ): UpdateManyWithGeneratedKeysSqlDefinitionPartiallyApplied[A, K] = {
    new UpdateManyWithGeneratedKeysSqlDefinitionPartiallyApplied[A, K] {
      def withChunkSize[F[_]](as: F[A], chunkSize: Int)(implicit
          F: Foldable[F]
      ): Stream[ConnectionIO, K] = {
        given Read[K] = sqlDef.read
        update
          .updateManyWithGeneratedKeys(
            sqlDef.columns.toVector.map(_.sql.rawSql)*
          )
          .withChunkSize(as, chunkSize)
      }
    }
  }
}

trait UpdateManyWithGeneratedKeysSqlDefinitionPartiallyApplied[A, K] {
  def apply[F[_]](as: F[A])(implicit F: Foldable[F]): Stream[ConnectionIO, K] =
    withChunkSize(as, doobie.util.update.DefaultChunkSize)

  def withChunkSize[F[_]](as: F[A], chunkSize: Int)(implicit
      F: Foldable[F]
  ): Stream[ConnectionIO, K]
}
