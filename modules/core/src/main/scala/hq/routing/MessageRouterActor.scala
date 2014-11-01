package hq.routing

import akka.actor._
import hq._
import nugget.core.actors.ActorWithComposableBehavior

import scala.collection.immutable.HashSet
import scala.collection.mutable


object MessageRouterActor {
  def id = "router"
  def path(implicit f: ActorRefFactory) = f.actorSelection(s"/user/$id")
  def start(implicit f: ActorRefFactory) = f.actorOf(props, id)

  def props = Props(new MessageRouterActor())
}
class MessageRouterActor extends ActorWithComposableBehavior {

  val updatesCache : mutable.Map[Subject, Update] = new mutable.HashMap[Subject, Update]()
  val subscribers : mutable.Map[Subject, Set[ActorRef]] = new mutable.HashMap[Subject, Set[ActorRef]]()
  val components : mutable.Map[ActorRef, Subject => Boolean] = new mutable.HashMap[ActorRef, Subject => Boolean]()

  override def commonBehavior(): Receive =
    messagesFromClientHandler orElse
      messagesFromProviderHandler orElse
      super.commonBehavior()


  def addSubscriber(ref: ActorRef, subject: Subject): Unit = {
    logger.info(s"New subscriber for $subject at $ref")
    context.watch(ref)

    subscribers.get(subject) match {
      case None =>
        logger.info(s"New subscription with component for $subject")
        publishToProviders(Subscribe(subject))
      case Some(x) =>
        logger.info(s"New listener for existing subscription with component for $subject")
    }

    subscribers += (subject -> (subscribers.getOrElse(subject, new HashSet[ActorRef]()) + ref))
    updatesCache.get(subject) foreach(sender() ! _)
  }

  def removeSubscriber(ref: ActorRef, subject: Subject): Unit = {
    val refs: Set[ActorRef] = subscribers.getOrElse(subject, new HashSet[ActorRef]()) - ref
    if (refs.isEmpty) {
      subscribers -= subject
      publishToProviders(Unsubscribe(subject))
      clearCacheFor(subject)
    } else subscribers += (subject -> refs)
  }

  def removeSubscriber(ref: ActorRef): Unit = {
    subscribers.collect {
      case (subj, set) if set contains ref => subj
    } foreach(removeSubscriber(ref, _))
  }

  def remember(update: Update) = updatesCache += (update.subj -> update)
  def clearCacheFor(subject: Subject) = updatesCache.remove(subject)


  def publishToProviders(msg: HQCommMsg): Unit = {
    components.foreach {
      case (ref, subjectMatch) if subjectMatch(msg.subj) =>
        logger.debug(s"$msg -> $ref")
        ref ! msg
    }
  }
  def publishToClients(msg: HQCommMsg): Unit = subscribers.get(msg.subj) foreach(_.foreach { actor =>
    actor ! msg
    logger.debug(s"s2c: $msg -> $actor")
  })

  def processUpdate(update: Update): Unit = publishToClients(update)

  def register(ref: ActorRef, subjectToBoolean: (Subject) => Boolean): Unit = {
    components += ref -> subjectToBoolean
    logger.info(s"Registered new component at $ref")
  }




  def processImage(update: Update, canBeCached: Boolean): Unit = {
    if (canBeCached) remember(update) else clearCacheFor(update.subj)
    processUpdate(update)
  }

  def processFragment(update: Update): Unit = processUpdate(update)

  def messagesFromClientHandler : Receive = {
    case Subscribe(subj) => addSubscriber(sender(), subj)
    case Unsubscribe(subj) => removeSubscriber(sender(), subj)
    case Command(subj, data) => publishToProviders(Command(subj, data))
    case Terminated(ref) => removeSubscriber(ref)
  }

  def messagesFromProviderHandler : Receive = {
    case RegisterComponent(mapping) => register(sender(), mapping)
    case Image(upd: Update, canBeCached) => processImage(upd, canBeCached)
    case Fragment(upd: Update) => processFragment(upd)
  }

}
