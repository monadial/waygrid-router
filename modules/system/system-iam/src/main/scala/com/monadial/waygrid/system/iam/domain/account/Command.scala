package com.monadial.waygrid.system.iam.domain.account

import com.monadial.waygrid.common.domain.algebra.messaging.command.Command
import com.monadial.waygrid.common.domain.algebra.messaging.command.Value.CommandId
import com.monadial.waygrid.system.iam.domain.account.Value.AccountId

object Command:
  sealed trait AccountCommand extends Command
  final case class CreateAccount(id: CommandId, accountId: AccountId, name: String) extends AccountCommand


