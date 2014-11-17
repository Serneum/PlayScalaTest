name := "PlayTest"

version := "1.0"

lazy val files = (project in file("modules/files")).enablePlugins(PlayScala)
lazy val `playtest` = (project in file(".")).enablePlugins(PlayScala).dependsOn(files).aggregate(files)

PlayKeys.generateRefReverseRouter := false

scalaVersion := "2.11.1"

libraryDependencies ++= Seq( jdbc , anorm , cache , ws )

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  
