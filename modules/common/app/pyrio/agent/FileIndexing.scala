package pyrio.agent

import java.io.{FilenameFilter, File}

import com.google.common.cache.CacheBuilder
import com.google.common.collect.BiMap
import com.typesafe.scalalogging.LazyLogging

import scala.collection.parallel
import scala.collection.parallel.mutable
import scala.slick.jdbc.meta.MTable
import scala.util.matching.Regex


trait FileIndexingSession {
  def locateNextItem(l: Long): Option[Long]

  def getAbsoluteFileLocationAt(l: Long): Option[String]

  def tailIndex(): Option[Long]

  def close(): Unit
}

trait FileIndexing {
  def startSession(target: MonitorTarget): FileIndexingSession
}


class SimpleFileIndexing extends FileIndexing {
  override def startSession(target: MonitorTarget): FileIndexingSession = new H2FileIndexingSession(target)
}


class H2FileIndexingSession(target: MonitorTarget) extends FileIndexingSession with LazyLogging {

  import scala.slick.driver.H2Driver.simple._

  private lazy val db = {
    require(dir.exists() && dir.isDirectory)
    val db = Database.forURL(dbURL(dir), driver = "org.h2.Driver");
    logger.info(s"Initialised db connection: $db at $dir")
    db withSession {
      implicit session =>
        if (MTable.getTables("findex").list.isEmpty) {
          logger.info("Creating findex table")
          findexes.ddl.create
        }
    }
    db
  }
  val fileIDCache = scala.collection.mutable.Map[Long, String]()
  val fileNameCache = scala.collection.mutable.Map[String, Long]()
  private var lastSync = 0
  private val findexes = TableQuery[FileIndex]
  private val rfmt = target match {
    case t: RollingFileMonitorTarget => t
    case _ => throw new IllegalArgumentException(target.toString)
  }
  private val rolledFilePatternR = new Regex(rfmt.rollingLogPattern)
  private val mainLogPatternR = new Regex(rfmt.mainLogPattern)
  private val dir = new File(rfmt.directory)

  override def locateNextItem(id: Long): Option[Long] = {
    syncWithFS()
    nameByID(id).flatMap { currentName =>
      val nextId = getListOfFiles(dir).dropWhile(_ != currentName) match {
        case current :: next :: _ => idByName(next)
        case _ => None
      }
      logger.debug(s"Locating next id after $id ($currentName) = $nextId  " + getListOfFiles(dir).dropWhile(_ != currentName))
      nextId
    }
  }

  override def tailIndex(): Option[Long] = {
    syncWithFS()
    getListOfFiles(dir) match {
      case Nil => None
      case l => idByName(l.last)
    }
  }

  override def close(): Unit = {
    logger.debug(s"Closing H2 session at $dir")
  }

  override def getAbsoluteFileLocationAt(id: Long): Option[String] = {
    syncWithFS()
    nameByID(id).map(fullPath)
  }

  private def fullPath(name:String) = dir.getAbsolutePath + "/" + name

  private def insert(name: String, newId:Long) : Long = {
    db withSession {
      implicit session =>
        findexes += (name, newId)
    }
    logger.debug(s"New entry $name in $dir id=$newId ")
    newId
  }

  def invalidateCache() = {
    logger.debug("Invalidating cache")
    fileIDCache.clear()
    fileNameCache.clear()
  }

  private def update(name:String, newId:Long) : Long = {
    db withSession {
      implicit session =>
        (for {
          entity <- findexes if entity.name === name
        } yield entity.id) update(newId)
    }
    logger.debug(s"Updated $name id = $newId")
    invalidateCache()
    newId
  }

  def clearDb() = {
    db withSession {
      implicit session =>
        (for {
          entity <- findexes
        } yield entity).delete
    }
    invalidateCache()
  }

  def deleteFrom(fromId: Long) = {
    db withSession {
      implicit session =>
        if ((for {
          entity <- findexes if entity.id >= fromId
        } yield entity).delete > 0) invalidateCache()
    }
  }



  private def syncWithFS(): Unit = {
    logger.debug("Syncing with FS...")
    getListOfFiles(dir) match {
      case Nil => {
        clearDb()
        invalidateCache()
      }
      case head :: Nil => {
        idByName(head) match {
          case None => insert(head,0)
          case Some(currentId) if currentId != 0 => update(head,0)
          case _ => {}
        }
      }
      case listOfFiles => {
        val lastId = listOfFiles.foldLeft(-1L) {
          case (previousId,name) => {
            logger.debug(s"Next: $name, previous id was $previousId")
            idByName(name) match {
              case None => insert(name,previousId+1)
              case Some(currentId) if previousId == -1 || currentId == previousId + 1 => currentId
              case Some(currentId) => {
                logger.info(s"Updating $name id $currentId -> ${previousId + 1}")
                update(name,previousId+1)
              }
            }
          }
        }
        //deleteFrom(lastId + 1)
      }
    }
  }

  private def dbURL(file: File): String = "jdbc:h2:" + file.getAbsolutePath + "/.idx"

  private def nameByID(id: Long): Option[String] = {
    fileIDCache.get(id) orElse {
      val value = getFromDB[String](_.id === id, _.name).headOption
      value.foreach(updateCache(id, _))
      value
    }
  }

  private def idByName(name: String): Option[Long] = {
    fileNameCache.get(name) orElse {
      val value = getFromDB[Long](_.name === name, _.id).headOption
      logger.debug(s"Trying to find value in the db for name=$name, result = $value")
      value.foreach(updateCache(_, name))
      value
    }
  }

  def updateCache(id: Long, name: String) = {
    fileIDCache += (id -> name)
    fileNameCache += (name -> id)
  }

  private def getFromDB[T](f: (FileIndex) => Column[Boolean], r: (FileIndex) => Column[T]): List[T] = {
    db withSession {
      implicit session =>
        (for {
          entity <- findexes if f(entity)
        } yield r(entity)).list
    }
  }

  private def getListOfFiles(dir: File): List[String] = {
    val list = getListOfFilesByPattern(dir, rolledFilePatternR) ::: getListOfFilesByPattern(dir, mainLogPatternR)
    logger.debug(s"List of files: $list")
    list
  }

  private def getListOfFilesByPattern(dir: File, pattern: Regex): List[String] = {
    dir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = pattern.findFirstIn(name).isDefined
    }).filter(_.isFile)
      .sortWith({
      case (f1, f2) =>
        if (f1.lastModified() != f2.lastModified()) {
          f1.lastModified() < f2.lastModified()
        } else {
          f1.getName.compareTo(f2.getName) < 0
        }
    })
      .map(_.getName).toList
  }

  class FileIndex(tag: Tag) extends Table[(String,Long)](tag, "findex") {
    def * = (name,id)
    def name = column[String]("name", O.PrimaryKey)
    def id = column[Long]("id")
  }
}

