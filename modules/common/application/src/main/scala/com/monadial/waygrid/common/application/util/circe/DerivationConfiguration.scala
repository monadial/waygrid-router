package com.monadial.waygrid.common.application.util.circe

import io.circe.derivation.Configuration

object DerivationConfiguration:
  given Configuration = Configuration
    .default
    .withDiscriminator("type")
    .withStrictDecoding
