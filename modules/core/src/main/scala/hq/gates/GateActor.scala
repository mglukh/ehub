package hq.gates

import akka.actor.{ActorRefFactory, Props}
import hq.{Subject, Update, Image, Subscribe}
import hq.routing.MessageRouterActor
import nugget.core.actors.PipelineWithStatesActor
import play.api.libs.json.Json

import scala.util.matching.Regex

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), id)
}
class GateActor(id: String) extends PipelineWithStatesActor {


  override def commonBehavior(): Receive = handleMessages orElse super.commonBehavior()

  override def becomeActive(): Unit = {}
  override def becomePassive(): Unit = {}

  def handleMessages : Receive = {
    case Subscribe(_) =>
      MessageRouterActor.path ! Image(Update(Subject("/gates/list/" + id), Json.obj(
        "name" -> id,
        "text" -> s"some random text from $id",
        "state" -> "passive"
      )))

  }

}