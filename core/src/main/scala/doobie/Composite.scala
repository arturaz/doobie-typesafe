package doobie

import cats.data.NonEmptyList
import cats.data.NonEmptyVector

import scala.Tuple.InverseMap
import scala.Tuple.IsMappedBy
import scala.annotation.targetName
import scala.annotation.unused
import scala.util.NotGiven
import scala.util.control.NonFatal

import implicits.*

object Composite {

  /** Removes the [[SQLDefinition]] wrapper from each member of the tuple.
    *
    * e.g. turns `(SQLDefinition[A], SQLDefinition[B])` into `(A, B)`.
    */
  type TupleValues[T <: Tuple] = InverseMap[T, SQLDefinition]

  /** Allows you to define a composite type that is composed of other
    * [[SQLDefinition]]s.
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
    * @param sqlDefinitionsTuple
    *   Tuple of [[SQLDefinition]]s to compose
    * @param map
    *   Function to map the tuple of components to the final result
    * @param unmap
    *   Function to map the final result to the tuple of components
    */
  def apply[T <: Tuple: IsMappedBy[SQLDefinition], R](
      sqlDefinitionsTuple: T
  )(map: TupleValues[T] => R)(unmap: R => TupleValues[T]): SQLDefinition[R] = {
    val sqlDefs = NonEmptyVector.fromVectorUnsafe(
      sqlDefinitionsTuple.productIterator
        .map(_.asInstanceOf[SQLDefinition[?]])
        .toVector
    )

    unsafe(sqlDefs, isOption = false) { iterator =>
      val untypedTuple = Tuple.fromArray(iterator.toArray)
//      println(
//        s"""---
//           |sqlDefs: $sqlDefs
//           |untypedTuple: $untypedTuple
//           |""".stripMargin)
      val tuple = untypedTuple.asInstanceOf[TupleValues[T]]
      map(tuple)
    }(r => unmap(r).productIterator)
  }

  /** Type-unsafe version of [[apply]].
    *
    * @param map
    *   Function to map the components that make up the composite type to the
    *   final result.
    * @param unmap
    *   Function to map the final result to the components that make up the
    *   composite type.
    */
  def unsafe[R](
      sqlDefinitions: NonEmptyVector[SQLDefinition[?]],
      isOption: Boolean
  )(map: Iterator[Any] => R)(unmap: R => Iterator[Any]): SQLDefinition[R] = {
    val isOpt = isOption

    new SQLDefinition[R] { self =>
      override type Self[X] = SQLDefinition[X]

      override def toString =
        s"Composite(columns: ${columns.iterator.map(_.rawName).mkString(", ")})"

      /** Amount of results to skip from the [[ResultSet]] when reading the Nth
        * tuple element.
        */
      lazy val sqlResultAccumulatedLengths: Vector[Int] =
        TypedMultiFragment.sqlResultAccumulatedLengths(sqlDefinitions.toVector)

      override lazy val columns: NonEmptyVector[Column[?]] =
        sqlDefinitions.flatMap(_.columns)

      override lazy val read =
        TypedMultiFragment.read(
          sqlDefinitions.toVector,
          sqlResultAccumulatedLengths
        )(map)

      override lazy val write = new Write(
        puts = sqlDefinitions.iterator.flatMap(_.write.puts).toList,
        toList = r => {
          val values = unmap(r)
          sqlDefinitions.iterator
            .zip(values)
            .flatMap { case (r, v) =>
              try {
                r.write.toList(v.asInstanceOf[r.Result])
              } catch {
                case NonFatal(e) =>
                  throw new Exception(
                    s"Error while writing $r with value $v",
                    e
                  )
              }
            }
            .toList
        },
        unsafeSet = (ps, idx, r) => {
          val values = unmap(r)
          sqlDefinitions.iterator
            .zip(sqlResultAccumulatedLengths)
            .zip(values)
            .foreach { case ((r, toSkip), v) =>
              r.write.unsafeSet(ps, idx + toSkip, v.asInstanceOf[r.Result])
            }
        },
        unsafeUpdate = (rs, idx, r) => {
          val values = unmap(r)
          sqlDefinitions.iterator
            .zip(sqlResultAccumulatedLengths)
            .zip(values)
            .foreach { case ((r, toSkip), v) =>
              r.write.unsafeUpdate(rs, idx + toSkip, v.asInstanceOf[r.Result])
            }
        }
      )

      override def imap[B](mapper: R => B)(
          contramapper: B => R
      ): SQLDefinition[B] =
        Composite(this)(mapper)(contramapper)

      override def isOption: Boolean = isOpt

      override def option[R1](using
          @unused ng: NotGiven[R =:= Option[R1]]
      ): SQLDefinition[Option[R]] = {
        unsafe(sqlDefinitions.map(_.option), isOption = true) { iterator =>
          enum Value {
            case Raw(v: Any)

            /** Represents the case when we do not have a value, but it is OK,
              * because that [[SQLDefinition]] is nullable.
              */
            case OptionNone
          }

          enum State {
            case Initial
            case None
            case Some(members: NonEmptyList[Value])

            /** Special case when we encounter first value that is `None` and it
              * happens to be a [[SQLDefinition.isOption]].
              *
              * In that case we do not know whether all subsequent values are
              * `Some` or `None` and have to wait for more values to decide.
              *
              * For example, for `Pets(None, "foo")` we would read `(None,
              * Some("foo")`, which translates to `Some(Pets(None, "foo"))`, or
              * `(None, None)`, which translates to `None`.
              */
            case Undecided(noneMembersEncounteredSoFar: Int)
            case ExpectedNoneButHadSomes(somes: NonEmptyList[(Any, Int)])
            case ExpectedSomesButHadNone(noneIndexes: NonEmptyList[Int])
          }

          val values = iterator.toVector

//          println(
//            s"""---
//               |sqlDefs: $sqlDefinitions
//               |values: $values
//               |""".stripMargin)

          val state = values.iterator
            .map(_.asInstanceOf[Option[?]])
            .zipWithIndex
            .foldLeft(State.Initial) { case (s, (a, idx)) =>
//              println(s"Iteration: state=$s, idx=$idx, value=$a")
              (s, a) match {
                case (State.Initial, None) =>
                  if (sqlDefinitions.getUnsafe(idx).isOption) State.Undecided(1)
                  else State.None
                case (State.Initial, Some(value)) =>
                  State.Some(NonEmptyList.one(Value.Raw(value)))

                case (State.Undecided(members), None) =>
                  if (sqlDefinitions.getUnsafe(idx).isOption)
                    State.Undecided(members + 1)
                  else
                    /** The [[SQLDefinition]] is not nullable, thus [[None]]
                      * here indicates an error.
                      */
                    State.ExpectedSomesButHadNone(NonEmptyList.one(idx))
                case (State.Undecided(members), Some(value)) =>
                  // If we have a `Some` value that means the record must be present, even with some nullable fields.
                  State.Some(
                    Value.Raw(value) :: NonEmptyList
                      .fromListUnsafe(List.fill(members)(Value.OptionNone))
                  )

                case (State.None, None) => State.None
                case (State.None, Some(value)) =>
                  State.ExpectedNoneButHadSomes(NonEmptyList.one((value, idx)))

                case (State.Some(members), None) =>
                  if (sqlDefinitions.getUnsafe(idx).isOption)
                    // It is OK for this to be None, because it is an option
                    State.Some(Value.OptionNone :: members)
                  else State.ExpectedSomesButHadNone(NonEmptyList.one(idx))
                case (State.Some(members), Some(value)) =>
                  State.Some(Value.Raw(value) :: members)

                case (v: State.ExpectedNoneButHadSomes, None) => v
                case (State.ExpectedNoneButHadSomes(somes), Some(value)) =>
                  State.ExpectedNoneButHadSomes((value, idx) :: somes)

                case (State.ExpectedSomesButHadNone(noneIndexes), None) =>
                  State.ExpectedSomesButHadNone(idx :: noneIndexes)
                case (v: State.ExpectedSomesButHadNone, Some(_)) => v
              }
            }

//          println(s"State: $state")

          state match {
            case State.Initial =>
              throw new IllegalStateException(
                "There should have been at least one element, but the iterator was empty."
              )
            case State.Undecided(_) | State.None =>
              // If all members are `None`, then the record is not present.
              None
            case State.Some(members) =>
              val membersInCorrectOrder =
                members.toList.reverseIterator.zipWithIndex.map {
                  case (a, idx) =>
                    val sqlDef = sqlDefinitions.getUnsafe(idx)

                    a match {
                      case Value.Raw(a) => if (sqlDef.isOption) Some(a) else a
                      case Value.OptionNone => None
                    }
                }
              val result = map(membersInCorrectOrder)
              Some(result)
            case State.ExpectedNoneButHadSomes(somes) =>
              val str = somes.toList.reverseIterator
                .map { case (value, idx) => s"  at $idx: $value" }
                .mkString("\n")
              throw new IllegalStateException(
                s"Expected all values to be `None`, but had some values that were `Some`:\n$str"
              )
            case State.ExpectedSomesButHadNone(noneIndexes) =>
              val str = noneIndexes.toList.reverseIterator
                .map(_.toString)
                .mkString(", ")
              throw new IllegalStateException(
                s"Expected all values to be `Some`, but had `None` values at indexes: $str"
              )
          }
        } {
          case None    => sqlDefinitions.iterator.map(_ => None)
          case Some(r) => unmap(r).map(Some(_))
        }
      }

      @targetName("bindColumns")
      override def ==>(value: R) = {
        val values = unmap(value)
        val pairs = sqlDefinitions.iterator
          .zip(values)
          .flatMap { case (sqlDef, value) =>
            (sqlDef ==> value.asInstanceOf[sqlDef.Result]).iterator
          }
          .toVector
        NonEmptyVector.fromVectorUnsafe(pairs)
      }

      @targetName("equals")
      override def ===(value: R) = {
        val values = unmap(value)
        val pairs =
          sqlDefinitions.iterator.zip(values).map { case (sqlDef, value) =>
            (sqlDef === value.asInstanceOf[sqlDef.Result]).fragment
          }
        sql"(${pairs.mkFragments(sql" AND ")})"
      }

      override def prefixedWith(prefix: String): SQLDefinition[R] = {
        unsafe(sqlDefinitions.map(_.prefixedWith(prefix)), isOption)(map)(unmap)
      }
    }
  }

  /** Overload for a single element tuple. */
  def apply[A, R](
      sqlDefinition: SQLDefinition[A]
  )(map: A => R)(unmap: R => A): SQLDefinition[R] =
    new SQLDefinition[R] {
      override type Self[X] = SQLDefinition[X]

      override def toString = sqlDefinition.toString()

      override def prefixedWith(prefix: String) =
        apply(sqlDefinition.prefixedWith(prefix))(map)(unmap)
      override val read = sqlDefinition.read.map(map)
      override val write = sqlDefinition.write.contramap(unmap)

      override def imap[B](mapper: R => B)(
          contramapper: B => R
      ): SQLDefinition[B] =
        apply(sqlDefinition)(map.andThen(mapper))(contramapper.andThen(unmap))

      override def isOption: Boolean = sqlDefinition.isOption

      def option[R1](using
          @unused ng: NotGiven[R =:= Option[R1]]
      ): SQLDefinition[Option[R]] =
        sqlDefinition.option.imap(_.map(map))(_.map(unmap))

      @targetName("bindColumns")
      override def ==>(value: R) = sqlDefinition ==> unmap(value)
      @targetName("equals")
      override def ===(value: R) = sqlDefinition === unmap(value)
      override def columns = sqlDefinition.columns
    }

  /** [[SQLDefinition]] when the element is defined by two [[Option]]al values.
    *
    * Usually the database constraints will enforce that both are either
    * [[Some]] or [[None]].
    *
    * When using this we need to introduce a separate type so Scala compiler
    * would know which [[Read]] and [[Write]] instances to use in SQL queries.
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
    */
  def fromMultiOption[A1, A2, R](
      a1Def: SQLDefinition[Option[A1]],
      a2Def: SQLDefinition[Option[A2]]
  )(map: (A1, A2) => R)(unmap: R => (A1, A2)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def)) {
      case (Some(a1), Some(a2)) => Some(map(a1, a2))
      case _                    => None
    } {
      case None => (None, None)
      case Some(r) =>
        val (a1, a2) = unmap(r)
        (Some(a1), Some(a2))
    }

  /** [[SQLDefinition]] when the element is defined by three [[Option]]al
    * values.
    *
    * Usually the database constraints will enforce that both are either
    * [[Some]] or [[None]].
    *
    * @see
    *   [[fromMultiOption]] overload for 2 [[Option]]al values for additional
    *   documentation.
    */
  def fromMultiOption[A1, A2, A3, R](
      a1Def: SQLDefinition[Option[A1]],
      a2Def: SQLDefinition[Option[A2]],
      a3Def: SQLDefinition[Option[A3]]
  )(
      map: (A1, A2, A3) => R
  )(unmap: R => (A1, A2, A3)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def)) {
      case (Some(a1), Some(a2), Some(a3)) => Some(map(a1, a2, a3))
      case _                              => None
    } {
      case None => (None, None, None)
      case Some(r) =>
        val (a1, a2, a3) = unmap(r)
        (Some(a1), Some(a2), Some(a3))
    }

  /** [[SQLDefinition]] when the element is defined by four [[Option]]al values.
    *
    * Usually the database constraints will enforce that both are either
    * [[Some]] or [[None]].
    *
    * @see
    *   [[fromMultiOption]] overload for 2 [[Option]]al values for additional
    *   documentation.
    */
  def fromMultiOption[A1, A2, A3, A4, R](
      a1Def: SQLDefinition[Option[A1]],
      a2Def: SQLDefinition[Option[A2]],
      a3Def: SQLDefinition[Option[A3]],
      a4Def: SQLDefinition[Option[A4]]
  )(
      map: (A1, A2, A3, A4) => R
  )(unmap: R => (A1, A2, A3, A4)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def, a4Def)) {
      case (Some(a1), Some(a2), Some(a3), Some(a4)) => Some(map(a1, a2, a3, a4))
      case _                                        => None
    } {
      case None => (None, None, None, None)
      case Some(r) =>
        val (a1, a2, a3, a4) = unmap(r)
        (Some(a1), Some(a2), Some(a3), Some(a4))
    }

  /** [[SQLDefinition]] when the element is defined by five [[Option]]al values.
    *
    * Usually the database constraints will enforce that both are either
    * [[Some]] or [[None]].
    *
    * @see
    *   [[fromMultiOption]] overload for 2 [[Option]]al values for additional
    *   documentation.
    */
  def fromMultiOption[A1, A2, A3, A4, A5, R](
      a1Def: SQLDefinition[Option[A1]],
      a2Def: SQLDefinition[Option[A2]],
      a3Def: SQLDefinition[Option[A3]],
      a4Def: SQLDefinition[Option[A4]],
      a5Def: SQLDefinition[Option[A5]]
  )(
      map: (A1, A2, A3, A4, A5) => R
  )(unmap: R => (A1, A2, A3, A4, A5)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def, a4Def, a5Def)) {
      case (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5)) =>
        Some(map(a1, a2, a3, a4, a5))
      case _ => None
    } {
      case None => (None, None, None, None, None)
      case Some(r) =>
        val (a1, a2, a3, a4, a5) = unmap(r)
        (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5))
    }

  /** [[SQLDefinition]] when the element is defined by six [[Option]]al values.
    *
    * Usually the database constraints will enforce that both are either
    * [[Some]] or [[None]].
    *
    * @see
    *   [[fromMultiOption]] overload for 2 [[Option]]al values for additional
    *   documentation.
    */
  def fromMultiOption[A1, A2, A3, A4, A5, A6, R](
      a1Def: SQLDefinition[Option[A1]],
      a2Def: SQLDefinition[Option[A2]],
      a3Def: SQLDefinition[Option[A3]],
      a4Def: SQLDefinition[Option[A4]],
      a5Def: SQLDefinition[Option[A5]],
      a6Def: SQLDefinition[Option[A6]]
  )(
      map: (A1, A2, A3, A4, A5, A6) => R
  )(unmap: R => (A1, A2, A3, A4, A5, A6)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def, a4Def, a5Def, a6Def)) {
      case (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5), Some(a6)) =>
        Some(map(a1, a2, a3, a4, a5, a6))
      case _ => None
    } {
      case None => (None, None, None, None, None, None)
      case Some(r) =>
        val (a1, a2, a3, a4, a5, a6) = unmap(r)
        (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5), Some(a6))
    }

  /** [[SQLDefinition]] when the element is defined by seven [[Option]]al
    * values.
    *
    * Usually the database constraints will enforce that both are either
    * [[Some]] or [[None]].
    *
    * @see
    *   [[fromMultiOption]] overload for 2 [[Option]]al values for additional
    *   documentation.
    */
  def fromMultiOption[A1, A2, A3, A4, A5, A6, A7, R](
      a1Def: SQLDefinition[Option[A1]],
      a2Def: SQLDefinition[Option[A2]],
      a3Def: SQLDefinition[Option[A3]],
      a4Def: SQLDefinition[Option[A4]],
      a5Def: SQLDefinition[Option[A5]],
      a6Def: SQLDefinition[Option[A6]],
      a7Def: SQLDefinition[Option[A7]]
  )(
      map: (A1, A2, A3, A4, A5, A6, A7) => R
  )(unmap: R => (A1, A2, A3, A4, A5, A6, A7)): SQLDefinition[Option[R]] =
    apply((a1Def, a2Def, a3Def, a4Def, a5Def, a6Def, a7Def)) {
      case (
            Some(a1),
            Some(a2),
            Some(a3),
            Some(a4),
            Some(a5),
            Some(a6),
            Some(a7)
          ) =>
        Some(map(a1, a2, a3, a4, a5, a6, a7))
      case _ => None
    } {
      case None => (None, None, None, None, None, None, None)
      case Some(r) =>
        val (a1, a2, a3, a4, a5, a6, a7) = unmap(r)
        (Some(a1), Some(a2), Some(a3), Some(a4), Some(a5), Some(a6), Some(a7))
    }
}
