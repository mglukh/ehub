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

package hq.routing

import javax.security.auth.Subject

import akka.actor._
import akka.cluster.Cluster
import common.actors.{NodeInfo, ActorWithClusterAwareness, ActorWithComposableBehavior, ActorWithSubscribers}
import hq._

import scala.collection.mutable
import scala.concurrent.duration.DurationLong


object MessageRouterActor {
  def path(implicit f: ActorRefFactory) =
    f.actorSelection("/user/" + id)

  def id = "router"

  def start(implicit f: ActorRefFactory, cluster: Cluster) = f.actorOf(props, id)

  def props(implicit cluster: Cluster) = Props(new MessageRouterActor())
}


case class ProviderState(ref: ActorRef, active: Boolean)

case class RemoveIfInactive(ref: ActorRef)

class MessageRouterActor(implicit val cluster: Cluster)
  extends ActorWithComposableBehavior
  with ActorWithSubscribers
  with ActorWithClusterAwareness {

  def myNodeIsTarget(subj: Subject) = subj.address == myAddress

  val updatesCache: mutable.Map[Subject, Update] = new mutable.HashMap[Subject, Update]()
  val staticRoutes: mutable.Map[String, ProviderState] = new mutable.HashMap[String, ProviderState]()

  implicit val ec = context.dispatcher

  override def commonBehavior(): Actor.Receive = handler orElse super.commonBehavior()


  override def onClusterMemberUp(info: NodeInfo): Unit = {
    collectSubjects(_.address==info.address.toString).foreach { subj =>
      forwardToClusterNode(info.address.toString,Subscribe(subj))
    }
  }


  override def onClusterMemberRemoved(info: NodeInfo): Unit = {
    collectSubjects(_.address == info.address.toString).foreach { subj =>
      publishToClients(Stale(subj))
    }
  }

  override def onClusterMemberUnreachable(info: NodeInfo): Unit = {
    collectSubjects(_.address == info.address.toString).foreach { subj =>
      publishToClients(Stale(subj))
    }
  }

  private def handler: Receive = {
    case Command(subj, data) => publishToProviders(Command(subj, data))
    case RegisterComponent(route, ref) => register(ref, route)
    case u: Update => processUpdate(u)
    case Terminated(ref) if isProviderRef(ref) =>
      staticRoutes.collect {
        case (route, thatRef) if ref == thatRef.ref => route
      } foreach { route =>
        staticRoutes += (route -> ProviderState(ref, active = false))
      }
      logger.info(s"Actor $ref terminated, pending removal in 30 sec")
      context.system.scheduler.scheduleOnce(30.seconds, self, RemoveIfInactive(ref))
    case RemoveIfInactive(ref) => removeRoute(ref)
  }


  def publishToProviders(msg: HQCommMsg): Unit = {
    val route = msg.subj.route
    if (myNodeIsTarget(msg.subj)) {
      forwardToClusterNode(msg.subj.address, msg)
    } else {
      staticRoutes.get(route) match {
        case Some(ProviderState(ref, true)) => ref ! msg
        case Some(ProviderState(ref, false)) =>
          logger.debug(s"Route $route is inactive, message dropped")
        case None => logger.warn(s"Unknown route $route, message dropped")
      }
    }
  }

  def register(ref: ActorRef, route: String): Unit = {
    staticRoutes += route -> ProviderState(ref, active = true)
    context.watch(ref)
    logger.info(s"Registered new static route $route -> $ref")
    collectSubjects{ subj => subj.route == route && myNodeIsTarget(subj)}.foreach { subj =>
      logger.info(s"Subscribing to $subj with downstream component")
      publishToProviders(Subscribe(subj))
    }
  }

  def processUpdate(update: Update): Unit = {
    if (update.canBeCached) remember(update) else clearCacheFor(update.subj)
    publishToClients(update)
  }

  def remember(update: Update) = updatesCache += (update.subj -> update)

  def clearCacheFor(subject: Subject) = updatesCache.remove(subject)

  def publishToClients(msg: HQCommMsg): Unit = subscribersFor(msg.subj) foreach (_.foreach { actor =>
    actor ! msg
    logger.debug(s"s2c: $msg -> $actor")
  })

  def removeRoute(ref: ActorRef): Unit = {
    val routesToRemove = staticRoutes.collect {
      case (route, ProviderState(thatRef, false)) if ref == thatRef => route
    }
    routesToRemove foreach { route =>
      logger.info(s"Removing route: $route")
      
      collectSubjects { subj =>
          subj.route == route && myNodeIsTarget(subj)
      } foreach { subj =>
          publishToClients(Stale(subj))
      }
      context.unwatch(ref)
      staticRoutes.remove(route);
    }
  }

  def isProviderRef(ref: ActorRef) = staticRoutes.exists {
    case (route, provState) => provState.ref == ref
  }

  override def firstSubscriber(subject: Subject): Unit = {
    publishToProviders(Subscribe(subject))
  }

  override def lastSubscriberGone(subject: Subject): Unit = {
    publishToProviders(Unsubscribe(subject))
    clearCacheFor(subject)
  }

  override def processUnsubscribeRequest(ref: ActorRef, subject: Subject): Unit = {
  }

  override def processSubscribeRequest(ref: ActorRef, subject: Subject): Unit = {
    updatesCache.get(subject) foreach (ref ! _)
  }

}
