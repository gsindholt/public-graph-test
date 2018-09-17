import sbt.Keys._

name := "test-graph"

version := "0.1"

scalaVersion := "2.11.12"

libraryDependencies += "org.janusgraph" % "janusgraph-core" % "0.2.0" % "provided"

libraryDependencies += "org.apache.tinkerpop" % "gremlin-core" % "3.2.6" % "provided"

assemblyJarName in assembly := name.value + ".jar"