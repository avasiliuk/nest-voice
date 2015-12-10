package com.avasiliuk.nest.voice

import java.lang.Thread.UncaughtExceptionHandler
import javax.sound.sampled._

import akka.actor.{Actor, ActorLogging, PoisonPill}
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
          line.open(format)
          line.start()
          log.debug("Start sound capturing...")
          val ais: AudioInputStream = new AudioInputStream(line)
          log.debug("Start sound recording...")
          while (true) {
            print(" " + ais.read())
          }
          //          AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new OutputStream {
          //            override def write(b: Int): Unit = print(" " + b)
          //          })
          //          AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File("test.wav"))
          log.debug("Stop sound recording...")
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

  def volumeRMS(rawByte: Array[Byte]): Double = {
    if (rawByte.isEmpty) {
      return 0d
    }
    val raw = rawByte.map(_.toDouble)
    val sum = raw.sum
    val average = sum / raw.length

    val sumMeanSquare = 0d;
    for (int ii = 0;
    ii < raw.length;
    ii ++)
    {
      sumMeanSquare += Math.pow(raw[ii] - average, 2d);
    }
    double averageMeanSquare = sumMeanSquare / raw.length;
    double rootMeanSquare = Math.sqrt(averageMeanSquare);

    return rootMeanSquare;
  }
}

object MicrophoneActor {
  case class StartRecord()
}

