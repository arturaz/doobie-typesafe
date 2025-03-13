package doobie

import doobie.implicits.*
import doobie.util.DBFixture
import doobie.util.Helpers
import munit.CatsEffectSuite

class LeftJoinTest extends CatsEffectSuite with DBFixture with Helpers {
  private val withTable = db.mapAsync { xa =>
    val io = for {
      _ <- sql"create table $Person ($nameCol text not null)".update.run
      _ <-
        sql"create table $Pets ($nameCol text not null, $pet1Col text not null, $pet2Col text)".update.run
      _ <- Update[String](sql"INSERT INTO $Person ($nameCol) VALUES (?)".rawSql)
        .updateMany(
          Vector(
            "Alice",
            "Bob",
            "Charlie"
          )
        )
      _ <- Update[(String, String, Option[String])](
        sql"INSERT INTO $Pets ($nameCol, $pet1Col, $pet2Col) VALUES (?, ?, ?)".rawSql
      ).updateMany(
        Vector(
          ("Alice", "Fido", Some("Spot")),
          ("Bob", "Chuck", None)
        )
      )
    } yield ()

    io.transact(xa).map(_ => xa).unsafeToFuture()
  }

  withTable.test("left join") { xa =>
    val persons = Person `as` "person"
    val pets = Pets `as` "pet"
    val columns =
      Columns((persons(_.nameCol), pets(_.pet1Col.option), pets(_.pet2Col)))
    val select =
      sql"SELECT $columns FROM $persons LEFT JOIN $pets ON ${persons(_.nameCol) === pets(_ => nameCol)}"
        .query[(String, Option[String], Option[String])]

    val io = (for {
      rows <- select.to[List]
    } yield rows).transact(xa)

    assertIO(
      io,
      List(
        ("Alice", Some("Fido"), Some("Spot")),
        ("Bob", Some("Chuck"), None),
        ("Charlie", None, None)
      )
    )
  }

  withTable.test("composite: joined value is Some with Some inside") { xa =>
    val persons = Person `as` "person"
    val pets = Pets `as` "pet"
    val columns = Columns((persons(_.nameCol), pets(_.Row.option)))

    val select =
      sql"""SELECT $columns FROM $persons LEFT JOIN $pets ON ${persons(
          _.nameCol
        ) === pets(_ => nameCol)}
            WHERE ${persons(_.nameCol) === "Alice"}
         """
        .queryOf(columns)

    val io = (for {
      rows <- select.to[List]
    } yield rows).transact(xa)

    assertIO(
      io,
      List(
        ("Alice", Some(Pets.Row("Fido", Some("Spot"))))
      )
    )
  }

  withTable.test(
    "composite: joined value is Some with None inside as the 2nd column"
  ) { xa =>
    val persons = Person `as` "person"
    val pets = Pets `as` "pet"
    val columns = Columns((persons(_.nameCol), pets(_.Row.option)))

    val select =
      sql"""SELECT $columns FROM $persons LEFT JOIN $pets ON ${persons(
          _.nameCol
        ) === pets(_ => nameCol)}
            WHERE ${persons(_.nameCol) === "Bob"}
         """
        .queryOf(columns)

    val io = (for {
      rows <- select.to[List]
    } yield rows).transact(xa)

    assertIO(
      io,
      List(
        ("Bob", Some(Pets.Row("Chuck", None)))
      )
    )
  }

  withTable.test(
    "composite: joined value is Some with None inside as the 1st column"
  ) { xa =>
    val persons = Person `as` "person"
    val pets = Pets `as` "pet"
    val columns = Columns((persons(_.nameCol), pets(_.InvertedRow.option)))

    val select =
      sql"""SELECT $columns FROM $persons LEFT JOIN $pets ON ${persons(
          _.nameCol
        ) === pets(_ => nameCol)}
            WHERE ${persons(_.nameCol) === "Bob"}
         """
        .queryOf(columns)

    val io = (for {
      rows <- select.to[List]
    } yield rows).transact(xa)

    assertIO(
      io,
      List(
        ("Bob", Some(Pets.InvertedRow(None, "Chuck")))
      )
    )
  }

  withTable.test("composite: joined value is None") { xa =>
    val persons = Person `as` "person"
    val pets = Pets `as` "pet"
    val columns = Columns((persons(_.nameCol), pets(_.Row.option)))

    val select =
      sql"""SELECT $columns FROM $persons LEFT JOIN $pets ON ${persons(
          _.nameCol
        ) === pets(_ => nameCol)}
            WHERE ${persons(_.nameCol) === "Charlie"}
         """
        .queryOf(columns)

    val io = (for {
      rows <- select.to[List]
    } yield rows).transact(xa)

    assertIO(
      io,
      List(
        ("Charlie", None)
      )
    )
  }

  withTable.test("composite: with read-only composite") { xa =>
    val persons = Person `as` "person"
    val pets = Pets `as` "pet"

    case class Result(name: String, pet: Option[Pets.Row])
    val composite = Composite.readOnly(
      (persons(_.nameCol).sqlDefr, pets(_.Row.option).sqlDefr)
    )(Result.apply)

    val columns = Columns(composite)

    val select =
      sql"""SELECT $columns FROM $persons LEFT JOIN $pets ON ${persons(
          _.nameCol
        ) === pets(_ => nameCol)}
         """
        .queryOf(columns)

    val io = (for {
      rows <- select.to[List]
    } yield rows).transact(xa)

    assertIO(
      io,
      List(
        Result("Alice", Some(Pets.Row("Fido", Some("Spot")))),
        Result("Bob", Some(Pets.Row("Chuck", None))),
        Result("Charlie", None)
      )
    )
  }
}
