package agent.files

import java.nio.charset.Charset

import akka.actor.{Props, ActorSystem}
import akka.stream.actor.{ActorPublisher, ActorPublisherMessage}
import akka.stream.scaladsl.Flow
import akka.stream.{FlowMaterializer, MaterializerSettings}
import akka.util.{Timeout, ByteString}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.DurationDouble


/**
 * Created by L045682 on 15/09/2014.
 */
object NewTailer extends App {

/*  implicit val system = ActorSystem("Sys", ConfigFactory.load("akka.conf"))
  implicit val dispatcher = system.dispatcher
  implicit val charset = Charset.forName("UTF-8")
  implicit val fileIndexing = new FileIndexer
  implicit val timeout = Timeout(5.seconds)
  implicit val materializer = FlowMaterializer()
  val publisher = ActorPublisher[ProducedMessage[ByteString,FileCursor]](
    system.actorOf(Props(FileMonitorActorPublisher(1, RollingFileMonitorTarget("f:/tmp/log", "mylog[.]txt", "mylog-.+", f => f.lastModified()), None))))

  Flow(publisher).foreach(s => println(s.bs.utf8String))
*/
}
