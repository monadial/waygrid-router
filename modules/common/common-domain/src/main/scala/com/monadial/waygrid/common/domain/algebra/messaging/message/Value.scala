package com.monadial.waygrid.common.domain.algebra.messaging.message

import com.monadial.waygrid.common.domain.algebra.value.string.StringValue
import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue
import com.monadial.waygrid.common.domain.syntax.StringSyntax.toDomain

object Value:
  type MessageId = MessageId.Type
  object MessageId extends ULIDValue

  type MessageGroupId = MessageGroupId.Type
  object MessageGroupId extends ULIDValue

  type MessageType = MessageType.Type
  object MessageType extends StringValue:
    def fromMessage[M <: Message](message: M): MessageType =
      message
          .getClass
          .getCanonicalName
          .toDomain[MessageType]
