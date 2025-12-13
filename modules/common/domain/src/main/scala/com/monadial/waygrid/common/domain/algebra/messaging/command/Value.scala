package com.monadial.waygrid.common.domain.algebra.messaging.command

import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue

object Value:
  type CommandId = CommandId.Type
  object CommandId extends ULIDValue
