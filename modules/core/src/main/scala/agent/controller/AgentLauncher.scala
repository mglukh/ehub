package agent.controller

import agent.controller.storage.ConfigStorageActor
import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory

/**
 * Created by maks on 18/09/14.
 */
object AgentLauncher extends App {

  implicit val system =  ActorSystem("Agent",ConfigFactory.load("akka.conf"))

  implicit val config = ConfigFactory.load("agent.conf")

  ConfigStorageActor.start
  AgentControllerActor.start

}
