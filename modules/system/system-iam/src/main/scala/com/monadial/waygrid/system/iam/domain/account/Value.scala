package com.monadial.waygrid.system.iam.domain.account

import com.monadial.waygrid.common.domain.value.ulid.ULIDValue

object Value:
  type AccountId = AccountId.Type
  object AccountId extends ULIDValue

