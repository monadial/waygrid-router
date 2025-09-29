package com.monadial.waygrid.system.iam.settings

import com.monadial.waygrid.common.domain.model.settings.ServiceSettings
import io.circe.Codec

final case class IAMServiceSettings() extends ServiceSettings derives Codec.AsObject
