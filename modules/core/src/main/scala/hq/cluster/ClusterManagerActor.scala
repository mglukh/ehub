package hq.cluster

import akka.actor._
import akka.cluster.Cluster
import common.actors._
import hq.routing.MessageRouterActor
import hq.{RegisterComponent, Subject}
import play.api.libs.json.Json

/**
 * Created by maks on 6/11/2014.
 */
object ClusterManagerActor {
  def start(implicit f: ActorRefFactory, cluster: Cluster) = f.actorOf(props, id)

  def id = "cluster"

  def props(implicit cluster: Cluster) = Props(new ClusterManagerActor())

}


class ClusterManagerActor(implicit val cluster: Cluster)
  extends ActorWithComposableBehavior
  with ActorWithSubscribers
  with ActorWithClusterAwareness {

  val RouteId = ClusterManagerActor.id
  val RouteNodes = Subject(RouteId, "nodes")

  override def commonBehavior(): Actor.Receive = super.commonBehavior()

  override def preStart(): Unit = {
    MessageRouterActor.path ! RegisterComponent(RouteId, self)
    super.preStart()
  }

  override def onClusterChangeEvent(): Unit = {
    updateToAll(RouteNodes, nodesList)
  }

  def nodesList = Some(Json.toJson(nodes.map { x =>
    Json.obj(
      "id" -> x.address.toString,
      "address" -> x.address.toString,
      "state" -> status2string(x.state),
      "roles" -> x.roles
    )
  }.toArray))

  def status2string(status: NodeState): String = status match {
    case Up() => "up"
    case Unreachable() => "unreachable"
    case x => "unknown"
  }

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case RouteNodes =>
      logger.info("!>>> request!")
      updateTo(subject, ref, nodesList)
  }

}
