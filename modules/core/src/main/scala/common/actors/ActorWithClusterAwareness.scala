/*
 * Copyright 2014 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package common.actors

import akka.actor.{Actor, ActorRef, Address}
import akka.cluster.ClusterEvent._
import akka.util.Timeout
import hq.routing.MessageRouterActor

import scala.collection.immutable.HashMap
import scala.concurrent.duration._
import scala.util.{Failure, Success}


sealed trait NodeState

case class Up() extends NodeState

case class Unreachable() extends NodeState

case class NodeInfo(state: NodeState, address: Address, roles: Set[String]) extends Comparable[NodeInfo] {
  override def compareTo(o: NodeInfo): Int = address.toString.compareTo(o.address.toString)
}

case class ClusterActorId(address: String, id: String)


trait ActorWithClusterAwareness extends ActorWithCluster {

  var nodes: List[NodeInfo] = List[NodeInfo]()
  private var refCache: Map[ClusterActorId, ActorRef] = new HashMap[ClusterActorId, ActorRef]()

  def forwardToClusterNode(address: String, msg: Any): Unit = {
    if (nodeIsUp(address)) {
      locateRefFor(address, MessageRouterActor.id) match {
        case Some(ref) =>
          logger.debug(s"!>>> $msg -> $ref")
          ref ! msg
        case None =>
          val sel = selectionFor(address, MessageRouterActor.id)
          logger.debug(s"!>>> $msg -> $sel")
          sel ! msg
      }
    } else {
      logger.info(s"Node $address is not up, message dropped")
    }
  }

  override def commonBehavior: Actor.Receive = commonMessagesHandler orElse super.commonBehavior

  def nodeIsUp(address: String): Boolean = nodes.exists {
    case node => node.address.toString == address && node.state == Up()
  }

  def onClusterMemberUp(info: NodeInfo): Unit = {}

  def onClusterMemberUnreachable(info: NodeInfo): Unit = {}

  def onClusterMemberRemoved(info: NodeInfo): Unit = {}

  def onClusterChangeEvent(): Unit = {}

  def resolveActorInCluster(address: String, id: String) = {
    implicit val timeout = Timeout(5.seconds)
    implicit val ec = context.dispatcher
    selectionFor(address, id).resolveOne() onComplete {
      case Success(result) => refCache += ClusterActorId(address, id) -> result
      case Failure(failure) => context.system.scheduler.scheduleOnce(5.seconds, self, ResolveRetry(address, id))
    }
  }

  def selectionFor(address: String, id: String) = {
    logger.info(s"!>>> using $context to resolve ${address + "/user/" + id} into " + context.actorSelection(address + "/user/" + id))
    context.actorSelection(address + "/user/" + id)
  }

  override def preStart(): Unit = {
    super.preStart()
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember], classOf[ReachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    super.postStop()
  }

  private def commonMessagesHandler: Actor.Receive = {
    case ResolveRetry(address, id) =>
      if (nodeIsUp(address)) resolveActorInCluster(address, id)
    case MemberUp(member) =>
      logger.info(s"Member is up: $member")
      val newNode = NodeInfo(Up(), member.address, member.roles)
      nodes = (nodes.filter(_.address != member.address) :+ newNode).sorted
      cleanRefCacheFor(member.address)
      resolveMessageRouter(member.address.toString)
      onClusterMemberUp(newNode)
      onClusterChangeEvent()
    case UnreachableMember(member) =>
      logger.info(s"Member is unreachable: $member")
      cleanRefCacheFor(member.address)
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
      cleanRefCacheFor(member.address)
      nodes = nodes.map {
        case b if b.address == member.address => b.copy(state = Up())
        case b => b
      }
      resolveMessageRouter(member.address.toString)
      nodes.collectFirst {
        case node if node.address == member.address => node
      } foreach onClusterMemberUp
      onClusterChangeEvent()
    case MemberRemoved(member, previousStatus) =>
      logger.info(s"Member is removed: $member")
      cleanRefCacheFor(member.address)
      nodes.collectFirst {
        case node if node.address == member.address => node
      } foreach onClusterMemberRemoved
      nodes = nodes.filter(_.address != member.address)
      onClusterChangeEvent()
    case x: MemberEvent =>
      logger.info(s"Member event: $x")

  }

  private def resolveMessageRouter(address: String) = resolveActorInCluster(address, MessageRouterActor.id)

  private def cleanRefCacheFor(address: Address) = {
    val addr = address.toString
    refCache = refCache.filter {
      case (key, value) => key.address != addr
    }
  }


  private def locateRefFor(address: String, id: String): Option[ActorRef] =
    refCache.get(ClusterActorId(address, id))


}

case class ResolveRetry(address: String, id: String)




