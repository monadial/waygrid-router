package com.monadial.waygrid.system.iam.domain.account

import com.monadial.waygrid.common.domain.algebra.ddd.AggregateHandleError

object Error:
  final case class AlreadyExists(id: Value.AccountId) extends AggregateHandleError
