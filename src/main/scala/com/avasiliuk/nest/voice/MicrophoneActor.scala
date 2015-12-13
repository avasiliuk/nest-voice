package com.avasiliuk.nest.voice

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.lang.Thread.State.NEW
import java.lang.Thread.UncaughtExceptionHandler
import javax.sound.sampled._

import akka.actor._
import com.avasiliuk.nest.voice.Globals.config
import com.avasiliuk.nest.voice.MicrophoneActor.{RecordedSpeech, StartRecord}
import net.sourceforge.javaflacencoder.FLACFileWriter
import org.slf4j.LoggerFactory

/**
  * Created by Aliaksandr Vasiliuk on 10.12.2015.
  */
class MicrophoneActor(sendTo: ActorRef) extends Actor with ActorLogging {
  val format: AudioFormat = new AudioFormat(16000, 8, 1, true, false)

  val info: DataLine.Info = new DataLine.Info(classOf[TargetDataLine], format)
  if (!AudioSystem.isLineSupported(info)) {
    log.error("Line not supported")
    context.stop(self)
  }

  val line = AudioSystem.getLine(info).asInstanceOf[TargetDataLine]
  val thread = new Thread("Microphone thread") {
    override def run(): Unit = {
      type FramesPerSecond = Array[Byte]

      def volumeRMS(second: Array[Byte]): Double = if (second.isEmpty) {
        0d
      } else {
        val doubles = second.map(_.toDouble)
        val sum = doubles.sum
        val average = sum / doubles.length
        val meanSquare = doubles.foldLeft(0d) { (acc, n) =>
          val norm = n - average
          acc + norm * norm
        } / doubles.length
        Math.sqrt(meanSquare)
      }

      def sendAudio(frames: List[FramesPerSecond]) = {
        log.debug(s"Sending audio. ${frames.size} seconds")
        val array: Array[Byte] = frames.flatMap(f => f).toArray[Byte]
        val is = new ByteArrayInputStream(array)
        val ais = new AudioInputStream(is, format, frames.size * format.getFrameRate.toInt)
        val os = new ByteArrayOutputStream()
        AudioSystem.write(ais, FLACFileWriter.FLAC, os)
        sendTo ! RecordedSpeech(os.toByteArray, format.getSampleRate.toInt)
      }

      line.open(format)
      line.start()
      log.debug("Start sound capturing...")
      val ais: AudioInputStream = new AudioInputStream(line)
      var second = new FramesPerSecond(format.getSampleRate.toInt)

      var read = ais.read(second)
      var recorded = List[FramesPerSecond]()
      var emptySeconds = 0
      while (read > 0) {
        val rms = volumeRMS(second)
        log.debug(s"Bytes:$read RMS:$rms Recorded:${recorded.size}")
        val hasWords = (read == format.getSampleRate.toInt) && (rms > config.getDouble("nest-voice.silence-threshold"))
        if (hasWords) {
          recorded = recorded :+ second
          emptySeconds = 0
        } else {
          if (emptySeconds < 1) {
            recorded = recorded :+ second
            emptySeconds += 1
          } else {
            if (recorded.size > emptySeconds) {
              sendAudio(recorded)
            }
            recorded = Nil
          }
        }
        second = new FramesPerSecond(format.getSampleRate.toInt)
        read = ais.read(second)
      }
      if (recorded.size > emptySeconds) sendAudio(recorded)
      log.debug("Stop sound capturing...")
    }
  }

  thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable): Unit = {
      LoggerFactory.getLogger(classOf[MicrophoneActor]).error("", e)
      context.self ! PoisonPill
    }
  })


  override def receive: Receive = {
    case StartRecord if thread.getState == NEW => thread.start()
  }

  override def postStop(): Unit = {
    line.stop()
    line.close()
  }
}

object MicrophoneActor {
  case class StartRecord()
  case class RecordedSpeech(audio: Array[Byte], sampleRate: Int)
  def props(sendTo: ActorRef) = Props(new MicrophoneActor(sendTo))
}

