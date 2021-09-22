name := "akka-typed-test"

version := "0.1"

scalaVersion := "2.13.6"

libraryDependencies ++= Seq (
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.16",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
