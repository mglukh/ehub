package hq.gates

import akka.actor._
import common.{BecomeActive, BecomePassive}
import common.actors.{ActorWithSubscribers, PipelineWithStatesActor}
import hq._
import hq.routing.MessageRouterActor
import play.api.libs.json.Json

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), id)
}

class GateActor(id: String) extends PipelineWithStatesActor with ActorWithSubscribers {

  def route = "gate/"+id

  val GATE_X_INFO = Subject(route, "info")

  val GATE_X_START = Subject(route, "start")
  val GATE_X_STOP = Subject(route, "stop")
  val GATE_X_KILL = Subject(route, "kill")

  var active = false

  override def preStart(): Unit = {
    super.preStart()
    MessageRouterActor.path ! RegisterComponent(route, self)
    context.parent ! GateAvailable(route)
  }

  override def commonBehavior(): Actor.Receive = commandHandler orElse super.commonBehavior()

  override def becomeActive(): Unit = {
    active = true
    updateToAll()
  }

  override def becomePassive(): Unit = {
    active = false
    updateToAll()
  }

  def updateToAll():Unit = updateToAll(GATE_X_INFO, info)

  def info = Some(Json.obj(
    "name" -> id,
    "text" -> s"some random text from $id",
    "state" -> (if (active) "active" else "passive")
  ))

  override def processSubscribeRequest(ref: ActorRef, subject: Subject) = subject match {
    case GATE_X_INFO => updateTo(subject, ref, info)
  }



  private def commandHandler : Receive = {
    case Command(GATE_X_STOP, _) =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the gate")
          self ! BecomePassive()
        case _ =>
          logger.info("Already stopped")
      }
    case Command(GATE_X_START, _) =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
        case _ =>
          logger.info("Starting the gate")
          self ! BecomeActive()
      }
    case Command(GATE_X_KILL, _) =>
      self ! PoisonPill
  }


}