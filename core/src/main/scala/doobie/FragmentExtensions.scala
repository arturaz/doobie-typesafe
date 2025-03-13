package doobie

implicit class FragmentExtensions(private val fragment: Fragment)
    extends AnyVal {

  /** Exposes the raw SQL of this fragment. */
  @inline def rawSql: String = fragment.internals.sql

  /** Converts untyped [[Fragment]] into a [[TypedFragment]]. */
  def typed[A](using Read[A]): TypedMultiFragment[A] = fragment

  /** So we wouldn't have discrepancy between the 0-column and N-column
    * variants.
    */
  @inline def queryOf[A](implicit read: Read[A]): Query0[A] =
    fragment.query[A]

  /** So we wouldn't have discrepancy between the 0-column and N-column
    * variants.
    */
  @inline def queryOf[A](label: String)(implicit read: Read[A]): Query0[A] =
    fragment.queryWithLabel[A](label)

  /** Returns the [[Query0]] for the given [[SQLDefinition]].
    *
    * Example:
    * {{{
    *   sql"""
    *   SELECT ${t.characterExperience} FROM $t
    *   WHERE ${t.user_id === userId} AND ${t.guid === character}
    *   LIMIT 1
    *   """.queryOf(t.characterExperience)
    * }}}
    */
  def queryOf[A](r: SQLDefinition[A]): Query0[A] =
    fragment.query[A](using r.read)

  /** Returns the [[Query0]] for the given [[SQLDefinition]].
    *
    * Example:
    * {{{
    *   sql"""
    *   SELECT ${t.characterExperience} FROM $t
    *   WHERE ${t.user_id === userId} AND ${t.guid === character}
    *   LIMIT 1
    *   """.queryOf(t.characterExperience)
    * }}}
    */
  def queryOf[A](r: SQLDefinition[A], label: String): Query0[A] =
    fragment.queryWithLabel[A](label)(using r.read)

  /** Returns the [[Query0]] for the given [[Columns]].
    *
    * Example:
    * {{{
    *   import tables.Characters as t
    *   val columns = Columns((t.characterExperience.sqlDef, t.characterLevel.sqlDef))
    *   sql"SELECT $columns FROM $t".queryOf(columns)
    * }}}
    */
  def queryOf[A](c: Columns[A]): Query0[A] =
    fragment.query[A](using c.read)

  /** Returns the [[Query0]] for the given [[Columns]].
    *
    * Example:
    * {{{
    *   import tables.Characters as t
    *   val columns = Columns((t.characterExperience.sqlDef, t.characterLevel.sqlDef))
    *   sql"SELECT $columns FROM $t".queryOf(columns)
    * }}}
    */
  def queryOf[A](c: Columns[A], label: String): Query0[A] =
    fragment.queryWithLabel[A](label)(using c.read)
}
