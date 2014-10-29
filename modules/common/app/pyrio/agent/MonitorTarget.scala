package pyrio.agent

import java.io.File

sealed trait MonitorTarget

case class RollingFileMonitorTarget(directory: String, mainLogPattern: String, rollingLogPattern: String, orderBy: (File) => Any) extends MonitorTarget
