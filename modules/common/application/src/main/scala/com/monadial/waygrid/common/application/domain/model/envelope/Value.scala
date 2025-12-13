package com.monadial.waygrid.common.application.domain.model.envelope

import com.monadial.waygrid.common.domain.algebra.value.bytes.BytesValue
import com.monadial.waygrid.common.domain.algebra.value.string.StringValue
import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue

object Value:
  type EnvelopeId = EnvelopeId.Type
  object EnvelopeId extends ULIDValue

  type MessageContentData = MessageContentData.Type
  object MessageContentData extends BytesValue

  type MessageContentType = MessageContentType.Type
  object MessageContentType extends StringValue

  final case class MessageContent(
    contentType: MessageContentType,
    contentData: MessageContentData
  )
