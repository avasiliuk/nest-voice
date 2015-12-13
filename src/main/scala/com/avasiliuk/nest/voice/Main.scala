package com.avasiliuk.nest.voice

import akka.actor._
import com.avasiliuk.nest.voice.Globals._
import com.avasiliuk.nest.voice.MicrophoneActor.StartRecord

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by Aliaksandr Vasiliuk on 10.12.2015.
  */
object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("nest-voice")
    system.actorOf(Props[MainActor], "main-actor")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}

class MainActor extends Actor with ActorLogging {

  val speechRecognition = context.actorOf(Props[SpeechRecognitionActor], "speech-recognition-actor")
  val microphone = context.actorOf(MicrophoneActor.props(speechRecognition), "microphone-actor")
  val nest = context.actorOf(NestActor.props(config.getString("nest-voice.nest-access-token"), config.getString("nest-voice.nest-url")), "nest-actor")

  context.watch(microphone)
  context.watch(speechRecognition)
  context.watch(nest)

  microphone ! StartRecord

  override def receive: Receive = {
    case Terminated => context.system.terminate()
  }
}
