package common

trait NowProvider {

  def now = System.currentTimeMillis()

}
