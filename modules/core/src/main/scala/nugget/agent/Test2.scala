package nugget.agent

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

/**
 * Created by maks on 16/09/14.
 */
object Test2 extends App {


  implicit val system = ActorSystem("ReceiverSystem", ConfigFactory.load("akka-hq.conf"))
  implicit val dispatcher = system.dispatcher

  class SimpleReceiver extends Actor with LazyLogging {
    override def receive: Receive = {
      case Acknowledgeable(msg, id) =>
        sender() ! Acknowledge(id)
        logger.info(s"Received: $msg")
        Thread.sleep(500)
    }
  }

  system.actorOf(Props[SimpleReceiver], "receiver")


}
