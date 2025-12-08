package com.monadial.waygrid.common.domain.model.scheduling

import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue

object Value:

  type TaskId = TaskId.Type
  object TaskId extends ULIDValue
