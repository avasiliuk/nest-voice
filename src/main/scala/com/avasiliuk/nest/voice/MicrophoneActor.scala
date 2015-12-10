package com.avasiliuk.nest.voice

import java.io.{File, ByteArrayInputStream, PipedOutputStream, PipedInputStream}
import java.lang.Thread.UncaughtExceptionHandler
import javax.sound.sampled._

import akka.actor.{Actor, ActorLogging, PoisonPill}
import com.avasiliuk.nest.voice.Globals.config
import com.avasiliuk.nest.voice.MicrophoneActor.StartRecord
import org.slf4j.LoggerFactory

/**
  * Created by Aliaksandr Vasiliuk on 10.12.2015.
  */
class MicrophoneActor extends Actor with ActorLogging {
  val format: AudioFormat = new AudioFormat(8000, 8, 1, true, true)

  val info: DataLine.Info = new DataLine.Info(classOf[TargetDataLine], format)
  if (!AudioSystem.isLineSupported(info)) {
    log.error("Line not supported")
    context.stop(self)
  }
  val line = AudioSystem.getLine(info).asInstanceOf[TargetDataLine]

  override def receive: Receive = {
    case StartRecord =>

      val thread = new Thread("Microphone thread") {
        override def run(): Unit = {
          type Frame = Array[Byte]

          def volumeRMS(frame: Array[Byte]): Double = if (frame.isEmpty) {
            0d
          } else {
            val doubles = frame.map(_.toDouble)
            val sum = doubles.sum
            val average = sum / doubles.length
            val meanSquare = doubles.foldLeft(0d) { (acc, n) =>
              val norm = n - average
              acc + norm * norm
            } / doubles.length
            Math.sqrt(meanSquare)
          }

          def sendAudio(frames: List[Frame]) = {
            log.debug(s"Sending audio. ${frames.size} frames")
            val array: Array[Byte] = frames.flatMap(f => f).toArray[Byte]
            val is = new ByteArrayInputStream(array)
            val ais = new AudioInputStream(is, format, frames.size)
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File("test.wav"))
          }

          line.open(format)
          line.start()
          log.debug("Start sound capturing...")
          val ais: AudioInputStream = new AudioInputStream(line)
          val frame = new Frame(format.getSampleRate.toInt)

          var read = ais.read(frame)
          var frames = List[Frame]()
          while (read > 0) {
            val rms = volumeRMS(frame)
            log.debug(s"Bytes:$read RMS:$rms")
            val hasWords = rms > config.getDouble("nest-voice.silence-threshold")
            if (hasWords) {
              frames = frames :+ frame
            } else {
              if (frames.nonEmpty) {
                sendAudio(frames)
              }
              frames = Nil
            }
            read = ais.read(frame)
          }
          //AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new OutputStream {
          //  override def write(b: Int): Unit = print(" " + b)
          //})
          //AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File("test.wav"))
          if (frames.nonEmpty) sendAudio(frames)
          log.debug("Stop sound capturing...")
        }
      }

      thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, e: Throwable): Unit = {
          LoggerFactory.getLogger(classOf[MicrophoneActor]).error("", e)
          context.self ! PoisonPill
        }
      })

      thread.start();
  }

  override def postStop(): Unit = {
    line.stop()
    line.close()
  }
}

object MicrophoneActor {
  case class StartRecord()
  case class AudioWithWords(array: Array[Byte])
}

