# Introduction

[doobie-typesafe](https://github.com/arturaz/doobie-typesafe) is a typesafe wrapper for [doobie](https://tpolecat.github.io/doobie/) that allows you 
to write queries in a typesafe way.

Goals of this library:

- Allow you to refer to SQL tables and columns in a typesafe way so that values of the wrong type cannot be used.

Non-goals:

- Provide a typesafe DSL for writing SQL queries. You still have to write the SQL yourself and validate it using 
  [doobie typechecking facilities](https://tpolecat.github.io/doobie/docs/06-Checking.html).

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "io.github.arturaz" % "doobie-typesafe" % "@VERSION@"
```

The code from `main` branch can be obtained with:
```scala
resolvers ++= Resolver.sonatypeOssRepos("snapshots")
libraryDependencies += "io.github.arturaz" % "doobie-typesafe" % "@SNAPSHOT_VERSION@"
```

**The library is only published for Scala 3** due to the use of 
[Scala 3 match types](https://docs.scala-lang.org/scala3/reference/new-types/match-types.html).

You can see all the published artifacts on 
[MVN Repository](https://mvnrepository.com/artifact/io.github.arturaz/doobie-typesafe_3), 
[Maven Central](https://search.maven.org/artifact/io.github.arturaz/doobie-typesafe_3),
[Sonatype](https://oss.sonatype.org/#nexus-search;quick~doobie-typesafe) or
[raw Maven repository](https://repo1.maven.org/maven2/io/github/arturaz/doobie-typesafe_3/).

## Credits

This library was created by [Artūras Šlajus](https://arturaz.net). You can find me as `arturaz` on the 
[Typelevel Discord Server](https://discord.gg/XF3CXcMzqD) in the `#doobie` channel.