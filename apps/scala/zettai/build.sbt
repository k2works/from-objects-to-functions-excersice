ThisBuild / scalaVersion := "3.3.9"
ThisBuild / version      := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "zettai",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"           % "2.13.0",
      "org.typelevel" %% "cats-effect"         % "3.7.1",
      "org.http4s"    %% "http4s-dsl"          % "0.23.38",
      "org.http4s"    %% "http4s-ember-server"  % "0.23.38",
      "org.http4s"    %% "http4s-ember-client"  % "0.23.38",
      "org.slf4j"      % "slf4j-nop"          % "2.0.17" % Test,
      "org.scalameta" %% "munit"               % "1.3.6"  % Test,
      "org.typelevel" %% "munit-cats-effect"   % "2.2.1"  % Test
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror")
  )
