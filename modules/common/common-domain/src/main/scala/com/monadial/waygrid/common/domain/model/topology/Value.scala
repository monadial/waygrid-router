package com.monadial.waygrid.common.domain.model.topology

import com.monadial.waygrid.common.domain.algebra.value.ulid.ULIDValue

object Value:
  type ContractId = ContractId.Type
  object ContractId extends ULIDValue

  type ClusterId = ClusterId.Type
  object ClusterId extends ULIDValue
