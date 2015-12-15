package com.avasiliuk.nest.voice

import akka.actor._
import com.avasiliuk.nest.voice.Globals._
import com.avasiliuk.nest.voice.Messages.{NestInitialized, StartSpeechRecord}

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

  val nestActor = context.actorOf(NestActor.props(
    self,
    config.getString("nest-voice.nest-access-token"),
    config.getString("nest-voice.nest-url"),
    config.getString("nest-voice.structure-name")
  ), "nest-actor")

  context.watch(nestActor)

  override def receive: Receive = {
    case Terminated => context.system.terminate()
    case NestInitialized =>
      val speechRecognition = context.actorOf(SpeechRecognitionActor.props(nestActor), "speech-recognition-actor")
      context.watch(speechRecognition)
      val microphone = context.actorOf(MicrophoneActor.props(speechRecognition), "microphone-actor")
      context.watch(microphone)
      microphone ! StartSpeechRecord
  }
}
