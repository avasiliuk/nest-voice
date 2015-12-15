package com.avasiliuk.nest.voice

/**
  * Created by Aliaksandr Vasiliuk on 15.12.2015.
  */
object Messages {
  case class NestInitialized()

  case class StartSpeechRecord()
  case class RecordedSpeech(audio: Array[Byte], sampleRate: Int)

  case class Recognized(text: String)
}
