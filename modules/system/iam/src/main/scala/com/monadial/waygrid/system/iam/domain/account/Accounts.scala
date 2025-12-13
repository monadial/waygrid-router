package com.monadial.waygrid.system.iam.domain.account

import com.monadial.waygrid.system.iam.domain.account.Value.AccountId

trait Accounts[F[+_]]:
  def save(account: Account): F[Unit]
  def find(id: AccountId): F[Option[Account]]
