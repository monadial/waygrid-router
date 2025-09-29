package com.monadial.waygrid.system.iam.domain.account

import com.monadial.waygrid.common.domain.algebra.ddd.{ AggregateHandleError, AggregateHandlerResult }
import com.monadial.waygrid.system.iam.domain.account.Command.{ AccountCommand, CreateAccount }
import com.monadial.waygrid.system.iam.domain.account.Event.{ AccountCreated, AccountEvent }
import com.monadial.waygrid.system.iam.domain.account.Value.AccountId

object Account extends AggregateRoot[AccountId, AccountState, AccountCommand, AccountEvent]:

  private val create: Handler

  override def handlers: List[Handler[AccountId, AccountState, AccountCommand, AccountEvent]] =



//  override def handle(
//    command: AccountCommand,
//    state: Option[AccountState]
//  ): Either[AggregateHandleError, AggregateHandlerResult[AccountId, AccountState, AccountEvent]] =
//    command match
//      case CreateAccount(id, name) =>
//        state match
//          case Some(_) => Left(Error.AlreadyExists(id))
//          case None =>
//            Right(AggregateHandlerResult.withEvent(AccountState.newState(id), AccountCreated(id, name)))
//      case _ => Left(AggregateHandleError.HandlerNotFound(command))
