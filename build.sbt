name := "TranslateBooks"
version := "1.0"
scalaVersion := "3.6.3"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-client" % "0.23.30",
  "org.http4s" %% "http4s-circe"        % "0.23.30",
  "io.circe"   %% "circe-generic"       % "0.14.6",
  "io.circe"   %% "circe-parser"        % "0.14.6",
  "org.typelevel" %% "cats-effect"      % "3.6.1",
  "com.github.cb372" %% "cats-retry" % "3.1.3",
  "org.typelevel" %% "munit-cats-effect-3" % "1.0.6" % Test
)


