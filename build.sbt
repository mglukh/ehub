Common.appSettings

lazy val common = (project in file("modules/common"))
  .enablePlugins(SbtWeb)
  .enablePlugins(PlayScala)

lazy val admin = (project in file("modules/admin"))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .dependsOn(common)

lazy val web = (project in file("modules/web"))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .dependsOn(common)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .aggregate(common, admin, web)
  .dependsOn(common, admin, web)

libraryDependencies ++= Common.commonDependencies