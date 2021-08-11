addSbtPlugin("org.scalameta"             % "sbt-scalafmt"         % "2.4.3")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"         % "0.1.20")
addSbtPlugin("com.typesafe.sbt"          % "sbt-native-packager"  % "1.8.1")
addSbtPlugin("org.scalameta"             % "sbt-native-image"     % "0.3.1")
addSbtPlugin("com.codecommit"            % "sbt-github-actions"   % "0.12.0")
addSbtPlugin("io.shiftleft"              % "sbt-ci-release-early" % "2.0.17")
addSbtPlugin("com.eed3si9n"              % "sbt-buildinfo"        % "0.10.0")
addSbtPlugin("com.dwijnand"              % "sbt-dynver"           % "4.1.1")
addSbtPlugin("com.thesamet"              % "sbt-protoc"           % "1.0.4")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"          % "0.5.2")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.5"
