package common.actors

import akka.actor.{Actor, Terminated, ActorRef}
import common.actors.ActorWithComposableBehavior
import hq.{Update, Unsubscribe, Subject, Subscribe}
import play.api.libs.json.JsValue

import scala.collection.immutable.HashSet
import scala.collection.mutable

/**
 * Created by maks on 4/11/2014.
 */
trait ActorWithSubscribers extends ActorWithComposableBehavior {

  private val subscribers : mutable.Map[Subject, Set[ActorRef]] = new mutable.HashMap[Subject, Set[ActorRef]]()

  private var watchedSubscribers : mutable.Set[ActorRef] = mutable.HashSet()

  override def commonBehavior(): Actor.Receive = handleMessages orElse super.commonBehavior()

  def firstSubscriber(subject: Subject) = {}
  def lastSubscriberGone(subject: Subject) = {}
  def processSubscribeRequest(ref: ActorRef, subject: Subject) = {}
  def processUnsubscribeRequest(ref: ActorRef, subject: Subject) = {}

  def collectSubjects(f: Subject => Boolean) = subscribers.collect {
    case (sub, set) if f(sub) => sub
  }
  def collectSubscribers(f: Subject => Boolean) = subscribers.filter {
    case (sub, set) => f(sub)
  }
  def subscribersFor(subj: Subject) = subscribers.get(subj)
  def updateTo(subj: Subject, ref: ActorRef, data: Option[JsValue]) =
    data foreach(ref ! Update(subj, _, canBeCached = true))
  def updateToAll(subj: Subject, data: Option[JsValue]) =
    subscribersFor(subj).foreach { set =>
      set.foreach { ref =>
        updateTo(subj, ref, data)
      }
    }

  def addSubscriber(ref: ActorRef, subject: Subject): Unit = {
    logger.info(s"New subscriber for $subject at $ref")

    if (!watchedSubscribers.contains(ref)) {
      watchedSubscribers += ref
      context.watch(ref)
    }

    subscribers.get(subject) match {
      case None =>
        logger.info(s"First subscriber for $subject: $ref")
        firstSubscriber(subject)
      case Some(x) =>
        logger.info(s"New subscriber for existing subscription for $subject")
    }

    subscribers += (subject -> (subscribers.getOrElse(subject, new HashSet[ActorRef]()) + ref))
    processSubscribeRequest(ref, subject)
  }

  def removeSubscriber(ref: ActorRef, subject: Subject): Unit = {
    val refs: Set[ActorRef] = subscribers.getOrElse(subject, new HashSet[ActorRef]()) - ref
    if (refs.isEmpty) {
      subscribers -= subject
      lastSubscriberGone(subject)
      logger.info(s"No more subscribers for $subject: $ref")
    } else subscribers += (subject -> refs)
    processUnsubscribeRequest(ref, subject)
  }

  private def removeSubscriber(ref: ActorRef): Unit = {
    context.unwatch(ref)
    watchedSubscribers -= ref
    subscribers.collect {
      case (subj, set) if set contains ref => subj
    } foreach(removeSubscriber(ref, _))
  }

  private def handleMessages : Receive = {
    case Subscribe(subj) =>
      addSubscriber(sender(), subj)
    case Unsubscribe(subj) =>
      removeSubscriber(sender(), subj)
    case Terminated(ref) if watchedSubscribers contains ref =>
      removeSubscriber(ref)
  }


}