package common.actors

import akka.actor.Address
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._


sealed trait NodeState

case class Up() extends NodeState

case class Unreachable() extends NodeState

case class NodeInfo(state: NodeState, address: Address, roles: Set[String]) extends Comparable[NodeInfo] {
  override def compareTo(o: NodeInfo): Int = address.toString.compareTo(o.address.toString)
}


trait ActorWithClusterAwareness extends ActorWithComposableBehavior {


  implicit val cluster: Cluster

  var nodes: List[NodeInfo] = List[NodeInfo]()


  override def commonBehavior(): Receive = commonMessagesHandler orElse super.commonBehavior()

  override def preStart(): Unit = {
    super.preStart()
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember], classOf[ReachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    super.postStop()
  }

  def onClusterMemberUp(info: NodeInfo): Unit = {}

  def onClusterMemberUnreachable(info: NodeInfo): Unit = {}

  def onClusterMemberReachable(info: NodeInfo): Unit = {}

  def onClusterMemberRemoved(info: NodeInfo): Unit = {}

  def onClusterChangeEvent(): Unit = {}

  private def commonMessagesHandler: Receive = {
    case MemberUp(member) =>
      logger.info(s"Member is up: $member")
      val newNode = NodeInfo(Up(), member.address, member.roles)
      nodes = (nodes.filter(_.address != member.address) :+ newNode).sorted
      onClusterMemberUp(newNode)
      onClusterChangeEvent()
    case UnreachableMember(member) =>
      logger.info(s"Member is unreachable: $member")
      nodes = nodes.map {
        case b if b.address == member.address => b.copy(state = Unreachable())
        case b => b
      }
      nodes.collectFirst {
        case node if node.address == member.address => node
      } foreach onClusterMemberUnreachable
      onClusterChangeEvent()
    case ReachableMember(member) =>
      logger.info(s"Member is reachable: $member")
      nodes = nodes.map {
        case b if b.address == member.address => b.copy(state = Up())
        case b => b
      }
      nodes.collectFirst {
        case node if node.address == member.address => node
      } foreach onClusterMemberReachable
      onClusterChangeEvent()
    case MemberRemoved(member, previousStatus) =>
      logger.info(s"Member is removed: $member")
      nodes.collectFirst {
        case node if node.address == member.address => node
      } foreach onClusterMemberRemoved
      nodes = nodes.filter(_.address != member.address)
      onClusterChangeEvent()
    case x: MemberEvent =>
      logger.info(s"Member event: $x")

  }


}



