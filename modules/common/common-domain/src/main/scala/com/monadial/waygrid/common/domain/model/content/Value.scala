package com.monadial.waygrid.common.domain.model.content

import com.monadial.waygrid.common.domain.algebra.value.bytes.BytesValue
import com.monadial.waygrid.common.domain.algebra.value.string.StringValue

object Value:
  type ContentType = ContentType.Type
  object ContentType extends StringValue

  type ContentData = ContentType.Type
  object ContentData extends BytesValue
