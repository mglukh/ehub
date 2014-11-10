package common.actors

import hq.{LocalSubj, RemoteSubj}

trait ActorWithRemoteSubscribers
  extends ActorWithSubscribers[RemoteSubj]
  with ActorWithCluster {

  override def convertSubject(subj: Any) : Option[RemoteSubj] = subj match {
    case local: LocalSubj => Some(RemoteSubj(myAddress, local))
    case remote: RemoteSubj => Some(remote)
    case _ => None
  }

}
