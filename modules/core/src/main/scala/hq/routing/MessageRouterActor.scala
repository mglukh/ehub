package hq.routing

import akka.actor
import akka.actor.FSM.->
import akka.actor._
import hq._
import nugget.core.actors.{ActorWithSubscribers, ActorWithComposableBehavior}

import scala.collection.immutable.HashSet
import scala.collection.mutable
import scala.concurrent.duration.{DurationLong, Duration}


object MessageRouterActor {
  def id = "router"
  def path(implicit f: ActorRefFactory) = f.actorSelection(s"/user/$id")
  def start(implicit f: ActorRefFactory) = f.actorOf(props, id)

  def props = Props(new MessageRouterActor())
}


case class ProviderState(ref: ActorRef, active: Boolean)
case class RemoveIfInactive(ref: ActorRef)

class MessageRouterActor extends ActorWithComposableBehavior
  with ActorWithSubscribers {

  val updatesCache : mutable.Map[Subject, Update] = new mutable.HashMap[Subject, Update]()
  val staticRoutes : mutable.Map[String, ProviderState] = new mutable.HashMap[String, ProviderState]()

  implicit val ec  = context.dispatcher

  override def commonBehavior(): Receive = handler orElse super.commonBehavior()

  override def firstSubscriber(subject: Subject): Unit = {
    publishToProviders(Subscribe(subject))
  }

  override def lastSubscriberGone(subject: Subject): Unit = {
    publishToProviders(Unsubscribe(subject))
    clearCacheFor(subject)
  }

  override def processUnsubscribeRequest(ref: ActorRef, subject: Subject): Unit = {}

  override def processSubscribeRequest(ref: ActorRef, subject: Subject): Unit = {
    updatesCache.get(subject) foreach(ref ! _)
  }

  def remember(update: Update) = updatesCache += (update.subj -> update)
  def clearCacheFor(subject: Subject) = updatesCache.remove(subject)


  def publishToProviders(msg: HQCommMsg): Unit = {
    val route = msg.subj.route
    staticRoutes.get(route) match {
      case Some(ProviderState(ref, true)) => ref ! msg
      case Some(ProviderState(ref, false)) =>
        logger.debug(s"Route $route is inactive, message dropped")
      case None => logger.warn(s"Unknown route $route, message dropped")
    }
  }

  def publishToClients(msg: HQCommMsg): Unit = subscribersFor(msg.subj) foreach(_.foreach { actor =>
    actor ! msg
    logger.debug(s"s2c: $msg -> $actor")
  })


  def register(ref: ActorRef, route: String): Unit = {
    staticRoutes += route -> ProviderState(ref, active = true)
    context.watch(ref)
    logger.info(s"Registered new static route $route -> $ref")
  }

  def processUpdate(update: Update): Unit = {
    if (update.canBeCached) remember(update) else clearCacheFor(update.subj)
    publishToClients(update)
  }

  def removeRoute(ref: ActorRef): Unit = {
    val routesToRemove = staticRoutes.collect {
      case (route, ProviderState(thatRef, false)) if ref == thatRef => route
    }
    routesToRemove foreach { route =>
      logger.info(s"Removing route: $route")
      collectSubscribers { _.route == route } foreach {
        case (sub,set) =>
          publishToClients(Discard(sub))
          set.foreach(removeSubscriber(_,sub))
      }
      context.unwatch(ref)
      staticRoutes.remove(route);
    }
  }

  def isProviderRef(ref: ActorRef) = staticRoutes.exists {
    case (route, provState) => provState.ref == ref
  }

  private def handler : Receive = {
    case Command(subj, data) => publishToProviders(Command(subj, data))
    case RegisterComponent(route, ref) => register(ref, route)
    case u : Update => processUpdate(u)
    case Terminated(ref) if isProviderRef(ref) =>
      staticRoutes.collect {
        case (route, thatRef) if ref==thatRef.ref => route
      } foreach { route =>
        staticRoutes += (route -> ProviderState(ref,active = false))
      }
      logger.info(s"Actor $ref terminated, pending removal in 30 sec")
      context.system.scheduler.scheduleOnce(30.seconds, self, RemoveIfInactive(ref))
    case RemoveIfInactive(ref) => removeRoute(ref)
  }

}
