package com.monadial.waygrid.system.scheduler.model.eventdata

import com.monadial.waygrid.system.scheduler.model.schedule.Value.{ScheduleTime, ScheduleToken}

final case class ScheduledTaskData(
  token: ScheduleToken,
  time: ScheduleTime
)
