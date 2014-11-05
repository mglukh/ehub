package agent.controller

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

/**
 * Created by maks on 18/09/14.
 */
object AgentLauncher extends App {

  implicit val system =  ActorSystem("Agent",ConfigFactory.load("akka.conf"))

  implicit val config = ConfigFactory.load("agent.conf")

  system.actorOf(AgentControllerActor.props, "controller")
}
