package com.avasiliuk.nest.voice

import java.io.IOException

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill}
import com.avasiliuk.nest.voice.SpeechRecognitionActor.{RecognizeSpeech, Recognized}
import com.squareup.okhttp._

/**
  * Created by Aliaksandr Vasiliuk on 11.12.2015.
  */
class SpeechRecognitionActor extends Actor with ActorLogging {
  val client = new OkHttpClient()
  val key = Globals.config.getString("nest-voice.google-speach-api-key")

  override def receive: Receive = {
    case RecognizeSpeech(audio, sampleRate, sendTo) =>
      val request = new Request.Builder()
        .url(s"https://www.google.com/speech-api/v2/recognize?output=json&client=chromium&lang=en-US&key=$key")
        .post(RequestBody.create(MediaType.parse(s"audio/x-flac; rate=$sampleRate;"), audio))
        .build()

      client.newCall(request).enqueue(new Callback {
        override def onFailure(request: Request, e: IOException): Unit = {
          log.error(e, "")
          self ! PoisonPill
        }

        override def onResponse(response: Response): Unit = {
          import org.json4s._
          import org.json4s.jackson.JsonMethods._
          val responseBody = response.body().string()
          log.debug(responseBody)
          val json = parse(responseBody)
          val JString(text) = (json \ "result" \ "alternative") (0) \ "transcript"
          sendTo ! Recognized(text)
        }
      })
  }
}

object SpeechRecognitionActor {
  case class RecognizeSpeech(audio: Array[Byte], sampleRate: Int, sendTo: ActorRef)
  case class Recognized(test: String)
}
