package com.avasiliuk.nest.voice

import akka.actor._
import com.avasiliuk.nest.voice.Globals._
import com.avasiliuk.nest.voice.MicrophoneActor.StartRecord
import com.avasiliuk.nest.voice.SpeechRecognitionActor.Recognized

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

  val nest = context.actorOf(ThermostatsActor.props(
    self,
    config.getString("nest-voice.nest-access-token"),
    config.getString("nest-voice.nest-url"),
    config.getString("nest-voice.structure-name")
  ), "nest-actor")

  context.watch(nest)

  override def receive: Receive = {
    case Terminated => context.system.terminate()
    case ThermostatsActor.Initialized =>
      val speechRecognition = context.actorOf(SpeechRecognitionActor.props(context.self), "speech-recognition-actor")
      context.watch(speechRecognition)
      val microphone = context.actorOf(MicrophoneActor.props(speechRecognition), "microphone-actor")
      context.watch(microphone)
      microphone ! StartRecord
    case Recognized(text) =>
  }
}
