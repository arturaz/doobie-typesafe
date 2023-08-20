package doobie

import cats.data.NonEmptyVector
import doobie.implicits.*
import doobie.util.{DBFixture, Helpers}
import munit.CatsEffectSuite

class TypedFragmentTest extends CatsEffectSuite with DBFixture with Helpers {
  val heightCol = Column[Option[Int]]("height")

  val withTable = db.mapAsync { xa =>
    val create = sql"create table $Person ($nameCol text not null, $ageCol int not null, $heightCol int)".update.run
    create.transact(xa).void.map(_ => xa).unsafeToFuture()
  }

  withTable.test("=== with value") { xa =>
    val typedFrag: TypedFragment[Option[Int]] = heightCol.sql

    val io = (for {
      _ <- insertInto(Person, NonEmptyVector.of(
        nameCol --> "Alice", ageCol --> Age(42), heightCol --> None
      )).update.run
      _ <- sql"UPDATE $Person SET ${typedFrag === Some(5)}".update.run
      result <- sql"SELECT $heightCol FROM $Person".queryOf(heightCol).unique
    } yield result).transact(xa)
    assertIO(io, Some(5))
  }
}
