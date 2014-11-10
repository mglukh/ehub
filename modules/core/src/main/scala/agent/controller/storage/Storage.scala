package agent.controller.storage

import java.io.File

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.slick.jdbc.meta.MTable

/**
 * Created by maks on 22/09/2014.
 */
trait Storage {

  def store(flowId: Long, config: String, state: Option[String]) : Unit
  def storeState(flowId: Long, state: Option[String]) : Unit
  def retrieve(flowId: Long) : Option[(String, Option[String])]
  def retrieveAll() : List[(Long, String, Option[String])]

}


object Storage extends StrictLogging {
  def apply(implicit config: Config): Storage = {
    val s: String = config.as[Option[String]]("agent.storage.provider") getOrElse "nugget.agent.controller.storage.H2Storage"
    Class.forName(s).getDeclaredConstructor(classOf[Config]).newInstance(config).asInstanceOf[Storage]
  }
}


case class H2Storage(implicit config: Config) extends Storage with StrictLogging {

  import scala.slick.driver.H2Driver.simple._

  private val dir = new File(config.as[Option[String]]("agent.storage.directory").getOrElse("."))

  private def dbURL(file: File): String = "jdbc:h2:" + dir.getAbsolutePath + "/storage"

  private lazy val db = {
    require(dir.exists() && dir.isDirectory)
    val db = Database.forURL(dbURL(dir), driver = "org.h2.Driver");
    logger.info(s"Initialised db connection: $db at $dir")
    db withSession {
      implicit session =>
        if (MTable.getTables("configurations").list.isEmpty) {
          logger.info("Creating configurations table")
          configurations.ddl.create
        }
    }
    db
  }

  load()

  private def load(): Unit = {
    db withSession { implicit session =>
    }
  }

  class Configurations(tag: Tag) extends Table[(Long, String, Option[String])](tag, "configurations") {
    def * = (flowId, conf, state)

    def flowId = column[Long]("tapId", O.PrimaryKey)

    def conf = column[String]("conf")

    def state = column[Option[String]]("state")
  }

  private def configurations = TableQuery[Configurations]

  override def store(flowId: Long, config: String, state: Option[String]): Unit = {
    db withSession { implicit session =>
      configurations.insertOrUpdate((flowId, config, state))
    }
  }

  override def retrieve(flowId: Long): Option[(String, Option[String])] = {
    db withSession { implicit session =>
      (configurations filter (_.flowId === flowId)).firstOption
    } map {
      case (f, c, s) => (c,s)
    }
  }

  override def storeState(flowId: Long, state: Option[String]): Unit = {
    db withSession { implicit session =>
      configurations
        .filter(_.flowId === flowId)
        .map(p => p.state)
        .update(state)
    }
  }

  override def retrieveAll(): List[(Long, String, Option[String])] = {
    db withSession { implicit session =>
      configurations.list
    }
  }
}
