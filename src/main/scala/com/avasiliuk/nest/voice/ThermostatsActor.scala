package com.avasiliuk.nest.voice

import akka.actor._
import com.avasiliuk.nest.voice.ThermostatsActor.{Initialized, Thermostat}
import com.firebase.client.Firebase.AuthResultHandler
import com.firebase.client._

import scala.collection.JavaConversions._

/**
  * Created by Aliaksandr Vasiliuk on 11.12.2015.
  */
class ThermostatsActor(replyTo: ActorRef, nestToken: String, firebaseURL: String, homeName: String) extends Actor with ActorLogging {
  val fbAll = new Firebase(firebaseURL)
  var thermostats = Map[String, Thermostat]()
  var initialized = false

  def logAndStop(firebaseError: FirebaseError) = {
    log.error("Firebase error " + firebaseError)
    self ! PoisonPill
  }

  fbAll.authWithCustomToken(nestToken, new AuthResultHandler {
    override def onAuthenticated(authData: AuthData): Unit = {
      fbAll.addValueEventListener(new ValueEventListener {
        override def onDataChange(dataSnapshot: DataSnapshot): Unit = context.self ! dataSnapshot
        override def onCancelled(firebaseError: FirebaseError): Unit = logAndStop(firebaseError)
      })
    }
    override def onAuthenticationError(firebaseError: FirebaseError): Unit = logAndStop(firebaseError)
  })

  override def receive: Receive = processShapshot

  def processShapshot: Receive = {
    case dataSnapshot: DataSnapshot =>
      val locs = for {
        structure <- dataSnapshot.child("structures").getChildren
        if structure.child("name").getValue().toString == homeName
        loc <- structure.child("wheres").getChildren
      } yield (loc.child("where_id").getValue.toString, loc.child("name").getValue().toString)
      val whereisMap = Map(locs.toSeq: _*)

      thermostats = dataSnapshot.child("devices").child("thermostats").getChildren.map { t =>
        val tm = Thermostat(id = t.child("device_id").getValue.toString,
          where = whereisMap.getOrElse(t.child("where_id").getValue.toString, ""),
          currentTemperature = t.child("ambient_temperature_f").getValue.toString.toInt,
          targetTemperature = t.child("target_temperature_f").getValue.toString.toInt)
        (tm.where, tm)
      }.toMap

      if (!initialized) {
        initialized = true
        replyTo ! Initialized
        context.become(processShapshot andThen processCommands, discardOld = true)
      }
      log.debug(s"Updated ${thermostats.size} thermostats")
  }

  def processCommands: Receive = {
    case _ =>
  }

}

object ThermostatsActor {
  case class Initialized()
  case class Thermostat(id: String, where: String, currentTemperature: Int, targetTemperature: Int)
  def props(replyTo: ActorRef, nestToken: String, firebaseURL: String, homeName: String) = Props(new ThermostatsActor(replyTo, nestToken, firebaseURL, homeName))
}
