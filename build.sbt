import com.timushev.sbt.updates.UpdatesPlugin.autoImport.dependencyUpdatesFilter
import sbt.Keys.parallelExecution
import sbt.{moduleFilter, _}

lazy val scala212 = "2.12.11"
lazy val supportedScalaVersions = List(scala212)

// factor out common settings
ThisBuild / scalaVersion := scala212
ThisBuild / scalacOptions ++= Seq("-target:jvm-1.8")

// Publishing Info
ThisBuild / credentials ++= Publishing.Creds
ThisBuild / homepage := Some(url("https://github.com/yugabyte/spark-cassandra-connector"))
ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt") )
ThisBuild / organization := "com.yugabyte.spark"
ThisBuild / organizationName := "Yugabyte"
ThisBuild / organizationHomepage := Some(url("https://www.yugabyte.com"))
ThisBuild / pomExtra := Publishing.OurDevelopers
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := Publishing.Repository
ThisBuild / scmInfo := Publishing.OurScmInfo
ThisBuild / version := Publishing.Version

Global / resolvers ++= Seq(
  DefaultMavenRepository,
  Resolver.sonatypeRepo("public"),
  "Staging for SparkcRc3" at "https://repository.apache.org/content/repositories/orgapachespark-1350/"
)

lazy val IntegrationTest = config("it") extend Test

lazy val integrationTestsWithFixtures = taskKey[Map[TestDefinition, Seq[String]]]("Evaluates names of all " +
  "Fixtures sub-traits for each test. Sets of fixture sub-traits names are used to form group tests.")

lazy val assemblySettings = Seq(
  assembly / parallelExecution := false,
  assembly / test := {},
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
    case PathList("META-INF", xs @ _*) => MergeStrategy.last
    case "module-info.class" => MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val commonSettings = Seq(
  // dependency updates check
  dependencyUpdatesFailBuild := true,
  dependencyUpdatesFilter -= moduleFilter(organization = "org.scala-lang" | "org.eclipse.jetty"),
  fork := true,
  parallelExecution := true,
  testForkedParallel := false,
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
) ++ assemblySettings

val annotationProcessor = Seq(
  "-processor", "com.datastax.oss.driver.internal.mapper.processor.MapperProcessor"
)

lazy val root = (project in file("."))
  .aggregate(connector, testSupport, driver)
  .settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    crossScalaVersions := Nil,
    publish / skip := true
  )

lazy val connector = (project in file("connector"))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*) //This and above enables the "it" suite
  .settings(commonSettings)
  .settings(
    crossScalaVersions := supportedScalaVersions,

    // set the name of the project
    name := "spark-cassandra-connector",

    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),

    // test grouping
    integrationTestsWithFixtures := {
      Testing.testsWithFixtures((testLoader in IntegrationTest).value, (definedTests in IntegrationTest).value)
    },

    IntegrationTest / testGrouping := Testing.makeTestGroups(integrationTestsWithFixtures.value),
    IntegrationTest / testOptions += Tests.Argument("-oF"),  // show full stack traces

    Test / javacOptions ++= annotationProcessor ++ Seq("-d", (classDirectory in Test).value.toString),

    Global / concurrentRestrictions := Seq(Tags.limitAll(Testing.parallelTasks)),

    libraryDependencies ++= Dependencies.Spark.dependencies
      ++ Dependencies.TestConnector.dependencies
      ++ Dependencies.Jetty.dependencies,

    scalacOptions in (Compile, doc) ++= Seq("-no-java-comments") //Scala Bug on inner classes, CassandraJavaUtil,
  )
  .dependsOn(
    testSupport % "test",
    driver
  )

lazy val testSupport = (project in file("test-support"))
  .settings(commonSettings)
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name := "spark-cassandra-connector-test-support",
    libraryDependencies ++= Dependencies.TestSupport.dependencies
  )

lazy val driver = (project in file("driver"))
  .settings(commonSettings)
  .settings(
    crossScalaVersions := supportedScalaVersions,
    name := "spark-cassandra-connector-driver",
    assembly /test := {},
    libraryDependencies ++= Dependencies.Driver.dependencies
      ++ Dependencies.TestDriver.dependencies
      :+ ("org.scala-lang" % "scala-reflect" % scalaVersion.value)
  )
