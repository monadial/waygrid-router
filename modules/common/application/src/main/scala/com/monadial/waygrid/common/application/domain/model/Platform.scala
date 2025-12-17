package com.monadial.waygrid.common.application.domain.model

object Platform:

  // If `numberOfAvailableCpuCores` is less than one, either your processor is about to die,
  // or your JVM has a serious bug in it, or the universe is about to blow up.
  inline def numberOfAvailableCpuCores: Int = Runtime.getRuntime.availableProcessors()
