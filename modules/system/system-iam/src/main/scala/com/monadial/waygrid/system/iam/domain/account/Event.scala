package com.monadial.waygrid.system.iam.domain.account

import com.monadial.waygrid.common.domain.algebra.messaging.event.Event

object Event:
  sealed trait AccountEvent extends Event
  case class AccountCreated(accountId: Value.AccountId, email: String) extends AccountEvent
