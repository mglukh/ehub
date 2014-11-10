package common.actors

import hq.{LocalSubj, RemoteSubj}

trait ActorWithLocalSubscribers
  extends ActorWithSubscribers[LocalSubj] {

  override def convertSubject(subj: Any): Option[LocalSubj] = subj match {
    case local: LocalSubj => Some(local)
    case remote: RemoteSubj => Some(remote.localSubj)
    case _ => None
  }

}
