package com.avasiliuk.nest.voice

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import com.firebase.client._

/**
  * Created by Aliaksandr Vasiliuk on 11.12.2015.
  */
class NestActor(nestToken: String, firebaseURL: String) extends Actor with ActorLogging {
  val fb = new Firebase(firebaseURL)
  fb.authWithCustomToken(nestToken, new Firebase.AuthResultHandler {
    override def onAuthenticationError(firebaseError: FirebaseError): Unit = {
      log.error("Firebase error " + firebaseError)
      self ! PoisonPill
    }

    override def onAuthenticated(authData: AuthData): Unit = {
      fb.addChildEventListener(new ChildEventListener {
        override def onChildRemoved(dataSnapshot: DataSnapshot): Unit = {
          log.debug(s"onChildRemoved:\n $dataSnapshot")
        }

        override def onChildMoved(dataSnapshot: DataSnapshot, s: String): Unit = {
          log.debug(s"onChildMoved:\n $dataSnapshot")
        }

        override def onChildChanged(dataSnapshot: DataSnapshot, s: String): Unit = {
          log.debug(s"onChildChanged:\n $dataSnapshot")
        }

        override def onCancelled(firebaseError: FirebaseError): Unit = {
          log.debug(s"onCancelled:\n $firebaseError")
        }

        override def onChildAdded(dataSnapshot: DataSnapshot, s: String): Unit = {
          log.debug(s"onChildAdded:\n $dataSnapshot")
        }
      })

      fb.addValueEventListener(new ValueEventListener {
        override def onDataChange(dataSnapshot: DataSnapshot): Unit = {
          log.debug(s"onDataChange:\n $dataSnapshot")
        }

        override def onCancelled(firebaseError: FirebaseError): Unit = {
          log.debug(s"onCancelled:\n $firebaseError")
        }
      })
    }

  })


  override def receive: Receive = {
    case x => println(x)
  }
}

object NestActor {
  def props(nestToken: String, firebaseURL: String) = Props(new NestActor(nestToken, firebaseURL))
}
