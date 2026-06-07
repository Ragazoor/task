import Dependencies._
import BuildHelper._

inThisBuild(
  List(
    organization := "io.github.ragazoor",
    homepage     := Some(url("https://github.com/Ragazoor/typed-future")),
    // Alternatively License.Apache2 see https://github.com/sbt/librarymanagement/blob/develop/core/src/main/scala/sbt/librarymanagement/License.scala
    licenses     := List("MIT" -> url("https://github.com/Ragazoor/typed-future?tab=MIT-1-ov-file#readme")),
    developers   := List(
      Developer(
        "Ragazoor",
        "Ragnar Englund",
        "eng.ragnar@gmail.com",
        url("https://github.com/ragazoor")
      )
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root =
  project
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(result, benchmark, examples)

lazy val result = module("task", "task")
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("io.github.ragazoor"))
  .settings(libraryDependencies += munit % Test)
  .settings(stdSettings("task"))

lazy val examples = module("examples", "examples")
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("io.github.ragazoor"))
  .settings(libraryDependencies += munit % Test)
  .settings(publish / skip := true)
  .settings(stdSettings("examples"))
  .dependsOn(result)

lazy val benchmark = module("benchmark", "benchmark")
  .enablePlugins(BuildInfoPlugin, JmhPlugin)
  .settings(buildInfoSettings("io.github.ragazoor"))
  .settings(libraryDependencies += munit % Test)
  .settings(publish / skip := true)
  .settings(stdSettings("benchmark"))
  .dependsOn(result)

def module(moduleName: String, fileName: String): Project =
  Project(moduleName, file(fileName))
    .settings(stdSettings(moduleName))

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
