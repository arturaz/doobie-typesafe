package doobie.util

import cats.effect.IO
import doobie.Transactor
import munit.*

import java.sql.{Connection, DriverManager}

trait DBFixture { self: FunSuite =>
  val db = FunFixture[Transactor.Aux[IO, Connection]](
    setup = _ => {
      // Load the driver
      val _ = Class.forName("org.h2.Driver")
      val connection = DriverManager.getConnection("jdbc:h2:mem:", "sa", null)
      Transactor.fromConnection[IO](connection, Some(doobie.logHandler))
    },
    teardown = _.kernel.close()
  )
}