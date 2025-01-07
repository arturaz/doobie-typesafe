# Introduction

[doobie-typesafe](https://github.com/arturaz/doobie-typesafe) is a typesafe wrapper for [doobie](https://tpolecat.github.io/doobie/) that allows you
to write queries in a typesafe way.

Goals of this library:

- Allow you to refer to SQL tables and columns in a typesafe way so that values of the wrong type cannot be used.

Non-goals:

- Provide a typesafe DSL for writing SQL queries. You still have to write the SQL yourself and validate it using
  [doobie typechecking facilities](https://tpolecat.github.io/doobie/docs/06-Checking.html).

## Next steps

Head over to [installation].

[installation]: 000_installation.md