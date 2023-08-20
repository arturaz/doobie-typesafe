package doobie

import doobie.implicits.*
import doobie.util.{DBFixture, Helpers}
import munit.CatsEffectSuite

class LeftJoinTest extends CatsEffectSuite with DBFixture with Helpers {
  val withTable = db.mapAsync { xa =>
    val io = for {
      _ <- sql"create table $Person ($nameCol text not null)".update.run
      _ <- sql"create table $Pets ($nameCol text not null, $pet1Col text not null, $pet2Col text)".update.run
      _ <- Update[String](sql"INSERT INTO $Person ($nameCol) VALUES (?)".rawSql).updateMany(Vector(
        "Alice", "Bob", "Charlie"
      ))
      _ <- Update[(String, String, Option[String])](
        sql"INSERT INTO $Pets ($nameCol, $pet1Col, $pet2Col) VALUES (?, ?, ?)".rawSql
      ).updateMany(Vector(
        ("Alice", "Fido", Some("Spot")),
        ("Bob", "Chuck", None),
      ))
    } yield ()

    io.transact(xa).map(_ => xa).unsafeToFuture()
  }

  withTable.test("left join") { xa =>
    val persons = Person as "person"
    val pets = Pets as "pet"
    val columns = Columns((persons(_.nameCol), pets(_.pet1Col.option), pets(_.pet2Col)))
    val select =
      sql"SELECT $columns FROM $persons LEFT JOIN $pets ON ${persons.c(_.nameCol) === pets.c(_ => nameCol)}"
        .query[(String, Option[String], Option[String])]

    val io = (for {
      rows <- select.to[List]
    } yield rows).transact(xa)

    assertIO(io, List(
      ("Alice", Some("Fido"), Some("Spot")),
      ("Bob", Some("Chuck"), None),
      ("Charlie", None, None)
    ))
  }
}
