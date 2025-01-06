package doobie

import cats.data.NonEmptyVector
import implicits.*

import scala.Tuple.{InverseMap, IsMappedBy}
import scala.annotation.targetName
import scala.util.control.NonFatal


object Composite {
  /**
   * Allows you to define a composite type that is composed of other [[SQLDefinition]]s.
   *
   * For example you can compose multiple [[Column]]s:
   * {{{
   *   case class Person(name: String, age: Int)
   *   val nameCol = Column[String]("name")
   *   val ageCol = Column[Int]("age")
   *   val person: SQLDefinition[Person] =
   *     Composite((nameCol.sqlDef, ageCol.sqlDef))(Person.apply)(Tuple.fromProductTyped)
   *
   *   // or alternatively
   *   val person: SQLDefinition[Person] =
   *     Composite((nameCol, ageCol).toSqlResults)(Person.apply)(Tuple.fromProductTyped)
   * }}}
   *
   * Or even other [[Composite]]s:
   * {{{
   *   val pet1Col = Column[String]("pet1")
   *   val pet2Col = Column[String]("pet2")
   *   case class Pets(pet1: String, pet2: String)
   *   val pets: SQLDefinition[Pets] = Composite((pet1Col.sqlDef, pet2Col.sqlDef))(Pets.apply)(Tuple.fromProductTyped)
   *
   *   case class PersonWithPets(person: Person, pets: Pets)
   *   val personWithPets: SQLDefinition[PersonWithPets] =
   *     Composite((person, pets))(PersonWithPets.apply)(Tuple.fromProductTyped)
   * }}}
   *
   * @param sqlDefinitionsTuple Tuple of [[SQLDefinition]]s to compose
   * @param map                 Function to map the tuple of components to the final result
   * @param unmap               Function to map the final result to the tuple of components
   * */
  def apply[T <: Tuple : IsMappedBy[SQLDefinition], R](
    sqlDefinitionsTuple: T
  )(map: InverseMap[T, SQLDefinition] => R)(unmap: R => InverseMap[T, SQLDefinition]): SQLDefinition[R] =
    new SQLDefinition[R] { self =>
      override type Self[X] = SQLDefinition[X]

      override def toString = s"Composite(columns: ${columns.iterator.map(_.rawName).mkString(", ")})"

      lazy val sqlDefinitions: Vector[SQLDefinition[?]] =
        sqlDefinitionsTuple.productIterator.map(_.asInstanceOf[SQLDefinition[?]]).toVector

      /** Amount of results to skip from the [[ResultSet]] when reading the Nth tuple element. */
      lazy val sqlResultAccumulatedLengths: Vector[Int] =
        sqlDefinitions.scanLeft(0)(_ + _.read.length).init

      override lazy val columns: NonEmptyVector[Column[?]] =
        NonEmptyVector.fromVectorUnsafe(sqlDefinitions.flatMap(_.columns.toVector))

      override lazy val read = new Read(
        gets = sqlDefinitions.toList.flatMap(_.read.gets),
        unsafeGet = (rs, idx) => {
          val values = Tuple.fromArray(sqlDefinitions.iterator.zip(sqlResultAccumulatedLengths).map { case (r, toSkip) =>
            val position = idx + toSkip
            try {
              r.read.unsafeGet(rs, position)
            }
            catch { case NonFatal(e) =>
              throw new Exception(s"Error while reading $r at position $position from a ResultSet", e)
            }
          }.toArray).asInstanceOf[InverseMap[T, SQLDefinition]]

          map(values)
        }
      )

      override lazy val write = new Write(
        puts = sqlDefinitions.toList.flatMap(_.write.puts),
        toList = r => {
          val values = unmap(r)
          sqlDefinitions.iterator.zip(values.productIterator)
            .flatMap { case (r, v) =>
              try {
                r.write.toList(v.asInstanceOf[r.Result])
              }
              catch { case NonFatal(e) =>
                throw new Exception(s"Error while writing $r with value $v", e)
              }
            }
            .toList
        },
        unsafeSet = (ps, idx, r) => {
          val values = unmap(r)
          sqlDefinitions.iterator.zip(sqlResultAccumulatedLengths).zip(values.productIterator)
            .foreach { case ((r, toSkip), v) =>
              r.write.unsafeSet(ps, idx + toSkip, v.asInstanceOf[r.Result])
            }
        },
        unsafeUpdate = (rs, idx, r) => {
          val values = unmap(r)
          sqlDefinitions.iterator.zip(sqlResultAccumulatedLengths).zip(values.productIterator)
            .foreach { case ((r, toSkip), v) =>
              r.write.unsafeUpdate(rs, idx + toSkip, v.asInstanceOf[r.Result])
            }
        }
      )

      override def imap[B](mapper: R => B)(contramapper: B => R): SQLDefinition[B] =
        Composite(this)(mapper)(contramapper)

      @targetName("bindColumns")
      override def ==>(value: R) = {
        val values = unmap(value)
        val pairs = sqlDefinitions.iterator.zip(values.productIterator).flatMap { case (sqlDef, value) =>
          (sqlDef ==> value.asInstanceOf[sqlDef.Result]).iterator
        }.toVector
        NonEmptyVector.fromVectorUnsafe(pairs)
      }

      @targetName("equals")
      override def ===(value: R) = {
        val values = unmap(value)
        val pairs = sqlDefinitions.iterator.zip(values.productIterator).map { case (sqlDef, value) =>
          (sqlDef === value.asInstanceOf[sqlDef.Result]).fragment
        }
        sql"(${pairs.mkFragments(sql" AND ")})"
      }

      override def prefixedWith(prefix: String): SQLDefinition[R] = {
        val prefixedTuple = Tuple.fromArray(
          sqlDefinitionsTuple.productIterator.map(_.asInstanceOf[SQLDefinition[?]].prefixedWith(prefix)).toArray
        ).asInstanceOf[T]
        apply(prefixedTuple)(map)(unmap)
      }
    }

  /** Overload for a single element tuple. */
  def apply[A, R](sqlDefinition: SQLDefinition[A])(map: A => R)(unmap: R => A): SQLDefinition[R] =
    new SQLDefinition[R] {
      override type Self[X] = SQLDefinition[X]

      override def toString = sqlDefinition.toString()

      override def prefixedWith(prefix: String) = apply(sqlDefinition.prefixedWith(prefix))(map)(unmap)
      override val read = sqlDefinition.read.map(map)
      override val write = sqlDefinition.write.contramap(unmap)

      override def imap[B](mapper: R => B)(contramapper: B => R): SQLDefinition[B] =
        apply(sqlDefinition)(map.andThen(mapper))(contramapper.andThen(unmap))

      @targetName("bindColumns")
      override def ==>(value: R) = sqlDefinition ==> unmap(value)
      @targetName("equals")
      override def ===(value: R) = sqlDefinition === unmap(value)
      override def columns = sqlDefinition.columns
    }

  /** 
   * [[SQLDefinition]] when the element is defined by two [[Option]]al values. 
   * 
   * Usually the database constraints will enforce that both are either [[Some]] or [[None]].
   * 
   * When using this we need to introduce a separate type so Scala compiler would know which [[Read]] and [[Write]]
   * instances to use in SQL queries.
   * 
   * Example:
   * {{{
   *   /** If the document type supports a total amount, it will be stored here. If this is [[Some]] then
   *     * [[colAmountCurrency]] will be [[Some]] as well, enforced by the database constraints.
   *     */
   *   val colAmount: Column[Option[BigDecimal]] = Column("amount")
   *   
   *   /** Same as [[colAmount]] but for the currency. */
   *   val colAmountCurrency: Column[Option[AppCurrency]] = Column("amount_currency")
   * 
   *   /** The amount of the document if the document type supports a total amount. */
   *   case class TotalAmount(m: Option[Money]) extends AnyVal
   *   object TotalAmount
   *       extends WithSQLDefinition[TotalAmount](
   *         Composite
   *           .fromMultiOption(colAmountCurrency.sqlDef, colAmount.sqlDef)(_.toSquants(_))(m =>
   *             (AppCurrency.fromSquants(m.currency).getOrThrow, m.amount)
   *           )
   *           .imap(TotalAmount(_))(_.m)
   *       )
   * }}}
   * */
  def fromMultiOption[A1, A2, R](
    a1Def: SQLDefinition[Option[A1]],
    a2Def: SQLDefinition[Option[A2]]
  )(map: (A1, A2) => R)(unmap: R => (A1, A2)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def)) { 
      case (Some(a1), Some(a2)) => Some(map(a1, a2))
      case _ => None
    } {
      case None => (None, None)
      case Some(r) => 
        val (a1, a2) = unmap(r)
        (Some(a1), Some(a2))
    }

  /** 
   * [[SQLDefinition]] when the element is defined by three [[Option]]al values. 
   * 
   * Usually the database constraints will enforce that both are either [[Some]] or [[None]].
   * 
   * @see [[fromMultiOption]] overload for 2 [[Option]]al values for additional documentation.
   * */
  def fromMultiOption[A1, A2, A3, R](
    a1Def: SQLDefinition[Option[A1]],
    a2Def: SQLDefinition[Option[A2]],
    a3Def: SQLDefinition[Option[A3]]
  )(map: (A1, A2, A3) => R)(unmap: R => (A1, A2, A3)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def)) { 
      case (Some(a1), Some(a2), Some(a3)) => Some(map(a1, a2, a3))
      case _ => None
    } {
      case None => (None, None, None)
      case Some(r) => 
        val (a1, a2, a3) = unmap(r)
        (Some(a1), Some(a2), Some(a3))
    }

  /** 
   * [[SQLDefinition]] when the element is defined by four [[Option]]al values. 
   * 
   * Usually the database constraints will enforce that both are either [[Some]] or [[None]].
   * 
   * @see [[fromMultiOption]] overload for 2 [[Option]]al values for additional documentation.
   * */
  def fromMultiOption[A1, A2, A3, A4, R](
    a1Def: SQLDefinition[Option[A1]],
    a2Def: SQLDefinition[Option[A2]],
    a3Def: SQLDefinition[Option[A3]],
    a4Def: SQLDefinition[Option[A4]]
  )(map: (A1, A2, A3, A4) => R)(unmap: R => (A1, A2, A3, A4)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def, a4Def)) { 
      case (Some(a1), Some(a2), Some(a3), Some(a4)) => Some(map(a1, a2, a3, a4))
      case _ => None
    } {
      case None => (None, None, None, None)
      case Some(r) => 
        val (a1, a2, a3, a4) = unmap(r)
        (Some(a1), Some(a2), Some(a3), Some(a4))
    }

  /** 
   * [[SQLDefinition]] when the element is defined by five [[Option]]al values. 
   * 
   * Usually the database constraints will enforce that both are either [[Some]] or [[None]].
   * 
   * @see [[fromMultiOption]] overload for 2 [[Option]]al values for additional documentation.
   * */
  def fromMultiOption[A1, A2, A3, A4, A5, R](
    a1Def: SQLDefinition[Option[A1]],
    a2Def: SQLDefinition[Option[A2]],
    a3Def: SQLDefinition[Option[A3]],
    a4Def: SQLDefinition[Option[A4]],
    a5Def: SQLDefinition[Option[A5]]
  )(map: (A1, A2, A3, A4, A5) => R)(unmap: R => (A1, A2, A3, A4, A5)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def, a4Def, a5Def)) { 
      case (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5)) => Some(map(a1, a2, a3, a4, a5))
      case _ => None
    } {
      case None => (None, None, None, None, None)
      case Some(r) => 
        val (a1, a2, a3, a4, a5) = unmap(r)
        (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5))
    }

  /** 
   * [[SQLDefinition]] when the element is defined by six [[Option]]al values. 
   * 
   * Usually the database constraints will enforce that both are either [[Some]] or [[None]].
   * 
   * @see [[fromMultiOption]] overload for 2 [[Option]]al values for additional documentation.
   * */
  def fromMultiOption[A1, A2, A3, A4, A5, A6, R](
    a1Def: SQLDefinition[Option[A1]],
    a2Def: SQLDefinition[Option[A2]],
    a3Def: SQLDefinition[Option[A3]],
    a4Def: SQLDefinition[Option[A4]],
    a5Def: SQLDefinition[Option[A5]],
    a6Def: SQLDefinition[Option[A6]]
  )(map: (A1, A2, A3, A4, A5, A6) => R)(unmap: R => (A1, A2, A3, A4, A5, A6)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def, a4Def, a5Def, a6Def)) { 
      case (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5), Some(a6)) => Some(map(a1, a2, a3, a4, a5, a6))  
      case _ => None
    } {
      case None => (None, None, None, None, None, None)
      case Some(r) => 
        val (a1, a2, a3, a4, a5, a6) = unmap(r)
        (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5), Some(a6))
    }

  /** 
   * [[SQLDefinition]] when the element is defined by seven [[Option]]al values. 
   * 
   * Usually the database constraints will enforce that both are either [[Some]] or [[None]].
   * 
   * @see [[fromMultiOption]] overload for 2 [[Option]]al values for additional documentation.
   * */
  def fromMultiOption[A1, A2, A3, A4, A5, A6, A7, R](
    a1Def: SQLDefinition[Option[A1]],
    a2Def: SQLDefinition[Option[A2]],
    a3Def: SQLDefinition[Option[A3]],
    a4Def: SQLDefinition[Option[A4]],
    a5Def: SQLDefinition[Option[A5]],
    a6Def: SQLDefinition[Option[A6]],
    a7Def: SQLDefinition[Option[A7]]
  )(map: (A1, A2, A3, A4, A5, A6, A7) => R)(unmap: R => (A1, A2, A3, A4, A5, A6, A7)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def, a4Def, a5Def, a6Def, a7Def)) { 
      case (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5), Some(a6), Some(a7)) => Some(map(a1, a2, a3, a4, a5, a6, a7))
      case _ => None
    } {
      case None => (None, None, None, None, None, None, None)
      case Some(r) => 
        val (a1, a2, a3, a4, a5, a6, a7) = unmap(r)
        (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5), Some(a6), Some(a7))
    }
}