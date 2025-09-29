package com.monadial.waygrid.system.iam.domain.account

import com.monadial.waygrid.system.iam.domain.account.Value.AccountId

final case class AccountState(id: AccountId, version: Long) extends AggregateRootState[AccountId]

object AccountState:
  def newState(id: AccountId): AccountState = AccountState(id, 0L)

