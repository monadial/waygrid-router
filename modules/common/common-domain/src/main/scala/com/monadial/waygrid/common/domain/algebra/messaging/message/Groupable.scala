package com.monadial.waygrid.common.domain.algebra.messaging.message

import com.monadial.waygrid.common.domain.algebra.messaging.message.Value.MessageGroupId

trait Groupable:
  def groupId: MessageGroupId
