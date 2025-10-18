import sbt.Keys.libraryDependencies

import scala.collection.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    name := "MakanPlan",
    libraryDependencies ++= {
      // Determine OS version of JavaFX binaries
      val osName = System.getProperty("os.name") match {
        case n if n.startsWith("Linux")   => "linux"
        case n if n.startsWith("Mac")     =>
          val arch = System.getProperty("os.arch")
          if (arch == "aarch64" || arch == "arm64") "mac-aarch64" else "mac"
        case n if n.startsWith("Windows") => "win"
        case _                            => throw new Exception("Unknown platform!")
      }
      Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
        .map(m => "org.openjfx" % s"javafx-$m" % "21.0.4" classifier osName)
    },
    libraryDependencies ++= Seq("org.scalafx" %% "scalafx" % "21.0.0-R32",
      "org.scalikejdbc" %% "scalikejdbc"       % "4.3.0",
      "com.h2database"  %  "h2"                % "2.2.224",
      "org.apache.derby" % "derby" % "10.17.1.0",
      "org.apache.derby" % "derbytools" % "10.17.1.0",
      "org.apache.commons" % "commons-csv" % "1.10.0",
      "org.openjfx" % "javafx-media" % "21.0.4" classifier "mac-aarch64"
    )
  )
