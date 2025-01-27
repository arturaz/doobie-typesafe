package doobie

/** Generates some boring code.
  */
@main
def generate(): Unit = {
  val upTo = 22

  println("### object `Columns`")
  (2 to upTo).foreach { n =>
    val types = (1 to n).map(i => s"A$i")
    val typesStr = types.mkString(", ")
    val sqlDefTypes = types.map(t => s"TF[$t]")
    val sqlDefTypesStr = sqlDefTypes.mkString(", ")

    val columnsApplyCode =
      s"""def apply[$typesStr](t: ($sqlDefTypesStr)): Columns[($typesStr)] = new Columns[($typesStr)](t)"""
    println(columnsApplyCode)
  }
}
