addSbtPlugin("org.scalameta"             % "sbt-scalafmt"        % "2.4.3")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"        % "0.1.20")
addSbtPlugin("com.typesafe.sbt"          % "sbt-native-packager" % "1.8.1")
addSbtPlugin("ch.epfl.scala"             % "sbt-release-early"   % "2.1.1+10-c6ef3f60")
addSbtPlugin("com.thesamet"              % "sbt-protoc"          % "1.0.4")
addSbtPlugin("com.eed3si9n"              % "sbt-buildinfo"       % "0.10.0")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.4"
