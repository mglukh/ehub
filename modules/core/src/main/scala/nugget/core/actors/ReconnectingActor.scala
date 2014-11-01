package nugget.core.actors

import akka.actor.{Actor, ActorRef}
import akka.remote.DisassociatedEvent
import com.typesafe.scalalogging.{StrictLogging, LazyLogging}

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.{Failure, Success}

trait ReconnectingActor extends StrictLogging with Actor with WithRemoteActorRef {

  implicit private val ec = context.dispatcher


  private var peer : Option[ActorRef] = None

  def connectionEndpoint : String

  def remoteAssociationTimeout = 5.seconds

  def reconnectAttemptInterval = 3.seconds

  override final def remoteActorRef : Option[ActorRef] = peer

  def connected : Boolean = peer.isDefined

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    super.preStart()
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self, classOf[DisassociatedEvent])
    super.postStop()
  }


  def scheduleReconnect(duration: FiniteDuration = reconnectAttemptInterval) = {
    context.system.scheduler.scheduleOnce(duration, self, Associate())
  }

  def disconnect() : Unit = {
    if (peer.isDefined) self ! DisconnectedState()
    peer = None
  }

  def initiateReconnect() : Unit = {
    disconnect()
    val addr = actorSelection
    logger.info(s"Trying to connect to $addr")
    addr.resolveOne(remoteAssociationTimeout).onComplete {
      case Failure(x) => self ! AssociationFailed(x)
      case Success(ref) => self ! Connected(ref)
    }
  }

  private def actorSelection = context.actorSelection(connectionEndpoint)

  def handleReconnectMessages : Receive = {
    case Connected(ref) =>
      logger.info(s"Connected to $ref")
      peer = Some(ref)
      self ! ConnectedState()
    case Associate() =>
      initiateReconnect()
    case DisassociatedEvent(local, remote, inbound) =>
      logger.info("Disconnected... ")
      peer match {
        case Some(ref) if ref.path.address == remote =>
          peer = None
          self ! DisconnectedState()
        case _ => ()
      }
    case AssociationFailed(x) =>
      scheduleReconnect()
  }


  private case class Connected(ref: ActorRef)
  private case class Associate()

  case class AssociationFailed(cause : Throwable)
  case class ConnectedState()
  case class DisconnectedState()


}