package com.avasiliuk.nest.voice

import akka.actor._
import com.avasiliuk.nest.voice.MicrophoneActor.{Speech, StartRecord}
import com.squareup.okhttp.{MediaType, OkHttpClient, Request, RequestBody}

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
  val mic = context.actorOf(Props[MicrophoneActor], "microphone-actor")
  val speechRecognition = context.actorOf(Props[SpeechRecognitionActor], "speech-recognition-actor")
  context.watch(mic)
  mic ! StartRecord

  //  import context.dispatcher
  //  context.system.scheduler.scheduleOnce(30 seconds, mic, PoisonPill)

  override def receive: Receive = {
    case Terminated => context.system.terminate()
    case Speech(audio, sampleRate) => {
      val client = new OkHttpClient()
      val key = Globals.config.getString("nest-voice.google-speach-api-key")
      val request = new Request.Builder()
        .url(s"https://www.google.com/speech-api/v2/recognize?output=json&client=chromium&lang=en-US&key=$key")
        .post(RequestBody.create(MediaType.parse(s"audio/x-flac; rate=$sampleRate;"), audio))
        .build()
      val response = client.newCall(request).execute()
      println(response.body().string())
    }
  }
}
