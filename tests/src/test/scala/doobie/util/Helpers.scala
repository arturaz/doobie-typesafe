package doobie.util

import munit.FunSuite

import scala.concurrent.Future

trait Helpers { self: FunSuite =>
  extension [A](fixture: FunFixture[A]) {
    def map[B](f: A => B): FunFixture[B] =
      mapAsync(a => Future.successful(f(a)))

    def mapAsync[B](f: A => Future[B]): FunFixture[B] = {
      var lastA = Option.empty[A]

      FunFixture.async[B](
        setup = opts =>
          fixture
            .setup(opts)
            .flatMap { a =>
              lastA = Some(a)
              f(a)
            }(munitExecutionContext),
        teardown = _ => fixture.teardown(lastA.get)
      )
    }
  }
}
