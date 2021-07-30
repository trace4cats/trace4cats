ThisBuild / scalaVersion := Dependencies.Versions.scala213
ThisBuild / crossScalaVersions := Seq(Dependencies.Versions.scala213, Dependencies.Versions.scala212)
ThisBuild / githubWorkflowJavaVersions := Seq("adopt@1.11")

ThisBuild / githubWorkflowBuildPreamble += WorkflowStep.Run(
  commands = List("docker-compose up -d"),
  name = Some("Create and start Docker containers")
)
ThisBuild / githubWorkflowBuildPreamble += WorkflowStep.Sbt(
  List("scalafmtCheckAll", "scalafmtSbtCheck"),
  name = Some("Check formatting")
)
ThisBuild / githubWorkflowBuildPostamble += WorkflowStep.Run(
  commands = List("docker-compose down"),
  name = Some("Stop and remove Docker resources")
)

ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.Equals(Ref.Branch("series/0.11.x")))
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ciReleaseSonatype"),
    name = Some("Publish artifacts"),
    env = Map(
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)
ThisBuild / githubWorkflowPublishCond := Some("github.actor != 'mergify[bot]'")
ThisBuild / githubWorkflowPublishPreamble += WorkflowStep.Use(
  ref = UseRef.Public("crazy-max", "ghaction-import-gpg", "v3"),
  id = Some("import_gpg"),
  name = Some("Import GPG key"),
  params = Map("gpg-private-key" -> "${{ secrets.GPG_PRIVATE_KEY }}", "passphrase" -> "${{ secrets.PGP_PASS }}")
)

ThisBuild / githubWorkflowPublishPostamble ++= {
  val dockerhubUsername = "janstenpickle"
  val githubRunNumber = "${{ github.run_number }}"
  val releaseVersion = "${{ env.RELEASE_VERSION }}"

  val common = Seq(
    WorkflowStep.Use(ref = UseRef.Public("docker", "setup-buildx-action", "v1"), name = Some("Set up Docker Buildx")),
    WorkflowStep.Use(
      ref = UseRef.Public("docker", "login-action", "v1"),
      name = Some("Login to Dockerhub"),
      params = Map("username" -> dockerhubUsername, "password" -> "${{ secrets.DOCKERHUB }}")
    ),
    WorkflowStep.ComputeVar(
      name = "RELEASE_VERSION",
      cmd =
        "sbt -Dsbt.log.noformat=true --client 'inspect actual version' | grep \"Setting: java.lang.String\" | cut -d '=' -f2 | tr -d ' '"
    ),
    WorkflowStep.Use(
      ref = UseRef.Public("actions-ecosystem", "action-regex-match", "v2"),
      name = Some("Check RELEASE_VERSION"),
      id = Some("release-version-regex-check"),
      params = Map("text" -> releaseVersion, "regex" -> "^[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9A-Za-z-]+)?$")
    ),
    WorkflowStep
      .ComputeVar(name = "IS_TAGGED_RELEASE", cmd = "echo ${{ steps.release-version-regex-check.outputs.match != '' }}")
  )

  def perModule(module: String, isNativeImage: Boolean) = {
    val imageName = s"$dockerhubUsername/trace4cats-$module"

    val buildImage =
      if (isNativeImage)
        Seq(
          WorkflowStep.Sbt(
            name = Some(s"Build GraalVM native image for '$module'"),
            commands = List(s"project $module", "nativeImage")
          ),
          WorkflowStep.Use(
            ref = UseRef.Public("docker", "build-push-action", "v2"),
            name = Some(s"Build Docker image for '$module'"),
            params = Map(
              "file" -> s"modules/$module/src/main/docker/Dockerfile",
              "context" -> s"modules/$module/target/native-image",
              "tags" -> s"$imageName:$githubRunNumber",
              "push" -> "false",
              "load" -> "true"
            )
          )
        )
      else
        Seq(
          WorkflowStep.Sbt(
            name = Some(s"Build Docker image for '$module'"),
            commands =
              List(s"project $module", s"""set ThisBuild / version := "$githubRunNumber"""", "Docker / publishLocal")
          )
        )

    val pushVersionedImage =
      Seq(
        WorkflowStep.Run(
          name = Some(s"Push versioned Docker image for '$module'"),
          cond = Some("env.IS_TAGGED_RELEASE == 'true'"),
          commands = List(
            s"docker tag $imageName:$githubRunNumber $imageName:$releaseVersion",
            s"docker push $imageName:$releaseVersion"
          )
        )
      )

    buildImage ++ pushVersionedImage
  }

  common ++ Seq("agent" -> true, "agent-kafka" -> true, "collector-lite" -> true, "collector" -> false)
    .flatMap((perModule _).tupled)
}

ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / versionScheme := Some("semver-spec")

ThisBuild / licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
ThisBuild / developers := List(
  Developer(
    "janstenpickle",
    "Chris Jansen",
    "janstenpickle@users.noreply.github.com",
    url = url("https://github.com/janstepickle")
  ),
  Developer(
    "catostrophe",
    "λoλcat",
    "catostrophe@users.noreply.github.com",
    url = url("https://github.com/catostrophe")
  )
)
ThisBuild / homepage := Some(url("https://github.com/trace4cats/trace4cats"))
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/trace4cats/trace4cats"), "scm:git:git@github.com:trace4cats/trace4cats.git")
)
ThisBuild / organization := "io.janstenpickle"
ThisBuild / organizationName := "trace4cats"
