package com.monadial.waygrid.common.application.kafka

import com.monadial.waygrid.common.domain.algebra.value.bytes.BytesValue

object Value:

  type Key = Key.Type
  object Key extends BytesValue
