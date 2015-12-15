package com.avasiliuk.nest.voice

import akka.actor._
import com.avasiliuk.nest.voice.Messages.{NestInitialized, Recognized}
import com.avasiliuk.nest.voice.NestActor.{SetTargetTemperature, Thermostat}
import com.firebase.client.Firebase.{AuthResultHandler, CompletionListener}
import com.firebase.client._

import scala.collection.JavaConversions._
import scala.util.Try

/**
  * Created by Aliaksandr Vasiliuk on 11.12.2015.
  */
class NestActor(replyTo: ActorRef, nestToken: String, firebaseURL: String, homeName: String) extends Actor with ActorLogging {
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
          where = whereisMap.getOrElse(t.child("where_id").getValue.toString, "").toLowerCase(),
          currentTemperature = t.child("ambient_temperature_f").getValue.toString.toInt,
          targetTemperature = t.child("target_temperature_f").getValue.toString.toInt)
        (tm.where, tm)
      }.toMap

      if (!initialized) {
        initialized = true
        replyTo ! NestInitialized
        context.become(processShapshot orElse processCommands, discardOld = true)
      }
      log.debug(s"Synced ${thermostats.size} thermostats")
  }

  def processCommands: Receive = {
    case Recognized(text) =>
      val t = text.split("\\s").foldLeft(SetTargetTemperature()) { (tt, word) =>
        thermostats.get(word.toLowerCase).fold {
          tt.copy(targetTemperature = Try(Some(word.toInt)).getOrElse(None))
        } { thermostat =>
          tt.copy(id = Some(thermostat.id), where = Some(thermostat.where))
        }
      }

      if (t.id.isDefined && t.targetTemperature.isDefined) {
        fbAll.child(s"devices/thermostats/${t.id.get}/target_temperature_f").setValue(t.targetTemperature.get, new CompletionListener {
          override def onComplete(firebaseError: FirebaseError, firebase: Firebase): Unit = {
            if (firebaseError != null) {
              logAndStop(firebaseError)
            } else {
              log.debug(s"Set ${t.where.get} thermostat to ${t.targetTemperature.get}")
            }
          }
        })
      }
  }
}

object NestActor {
  case class SetTargetTemperature(id: Option[String] = None, where: Option[String] = None, targetTemperature: Option[Int] = None)
  case class Thermostat(id: String, where: String, currentTemperature: Int, targetTemperature: Int)
  def props(replyTo: ActorRef, nestToken: String, firebaseURL: String, homeName: String) = Props(new NestActor(replyTo, nestToken, firebaseURL, homeName))
}
