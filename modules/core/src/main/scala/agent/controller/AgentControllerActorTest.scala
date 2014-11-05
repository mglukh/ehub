package agent.controller

import agent.controller.AgentControllerActor
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._

/**
 * Created by maks on 18/09/14.
 */
object AgentControllerActorTest extends App {

  implicit val system =  ActorSystem("Agent",ConfigFactory.load("akka.conf"))

  implicit val config = ConfigFactory.load("agent.conf")

  system.actorOf(AgentControllerActor.props, "controller")
}
