package com.monadial.waygrid.common.domain.model

import com.monadial.waygrid.common.domain.value.string.StringValue

object Waygrid:

  lazy val appName = "waygrid"

  type Address = Address.Type
  object Address extends StringValue



