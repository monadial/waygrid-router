package com.monadial.waygrid.common.domain.model.command

import com.monadial.waygrid.common.domain.value.ulid.ULIDValue

object Value:
  type CommandId = CommandId.Type
  object CommandId extends ULIDValue
