name := "nest-voice"
version := "1.0"

scalaVersion := "2.11.7"

val akkaVersion = "2.4.1"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.typesafe" % "config" % "1.3.0",
  "commons-io" % "commons-io" % "2.4",
  "com.squareup.okhttp" % "okhttp" % "2.6.0",
  "net.sourceforge.javaflacencoder" % "java-flac-encoder" % "0.3.7",
  "org.json4s" %% "json4s-jackson" % "3.3.0",
  "com.firebase" % "firebase-client-android" % "1.1.1",
  "org.apache.httpcomponents" % "httpclient" % "4.5.1"
)


