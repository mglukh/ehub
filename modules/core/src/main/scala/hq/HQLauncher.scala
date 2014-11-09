package hq

import agent.controller.AgentControllerActor
import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import hq.agents.AgentsManagerActor
import hq.gates.GateManagerActor
import hq.routing.MessageRouterActor

/**
 * Created by maks on 18/09/14.
 */
object HQLauncher extends App {

  implicit val system =  ActorSystem("ehubhq",ConfigFactory.load("akka-hq.conf"))

  implicit val config = ConfigFactory.load("agent.conf")

  implicit val cluster = Cluster(system)


  MessageRouterActor.start
  GateManagerActor.start
  AgentsManagerActor.start
}
