package com.avasiliuk.nest.voice

import java.io.IOException

import akka.actor._
import com.avasiliuk.nest.voice.MicrophoneActor.RecordedSpeech
import com.avasiliuk.nest.voice.SpeechRecognitionActor.Recognized
import com.squareup.okhttp._

/**
  * Created by Aliaksandr Vasiliuk on 11.12.2015.
  */
class SpeechRecognitionActor(replyTo: ActorRef) extends Actor with ActorLogging {
  val client = new OkHttpClient()
  val key = Globals.config.getString("nest-voice.google-speach-api-key")

  override def receive: Receive = {
    case RecordedSpeech(audio, sampleRate) =>
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
          //eliminates strange response with 2 results (one empty)
          val prepared = responseBody.replaceAllLiterally("{\"result\":[]}", "")
          lazy val json = parse(prepared)
          if (prepared.nonEmpty && (json \ "result").children.nonEmpty) {
            val JString(text) = (json \ "result" \ "alternative") (0) \ "transcript"
            log.debug(s"Recognized as: $text")
            replyTo ! Recognized(text)
          } else {
            log.debug("empty recognition result")
          }
        }
      })
  }
}

object SpeechRecognitionActor {
  case class Recognized(test: String)
  def props(replyTo: ActorRef) = Props(new SpeechRecognitionActor(replyTo))
}
