package com.monadial.waygrid.common.domain.model.cryptography.hashing

import com.monadial.waygrid.common.domain.value.long.LongValue
import com.monadial.waygrid.common.domain.value.string.StringValue

object Value:

  type LongHash = LongHash.Type
  object LongHash extends LongValue

  type HexHash = HexHash.Type
  object HexHash extends StringValue
