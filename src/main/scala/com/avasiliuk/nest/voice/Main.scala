package com.avasiliuk.nest.voice

import akka.actor._
import com.avasiliuk.nest.voice.MicrophoneActor.StartRecord

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by Aliaksandr Vasiliuk on 10.12.2015.
  */
object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("NestVoice")
    system.actorOf(Props[MainActor], "myActor1")
    Await.result(system.whenTerminated, Duration.Inf)
  }
}

class MainActor extends Actor with ActorLogging {

  val mic = context.actorOf(Props[MicrophoneActor])
  mic ! StartRecord

  import context.dispatcher

  context.system.scheduler.scheduleOnce(10 seconds, mic, PoisonPill)

  override def receive: Receive = {
    case x => log.debug(x.toString)
  }
}