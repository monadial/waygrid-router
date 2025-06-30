package com.monadial.waygrid.common.application.domain.model.routing

import com.monadial.waygrid.common.application.instances.DurationInstances.given
import com.monadial.waygrid.common.domain.model.routing.Value.{DeliveryStrategy, RepeatPolicy, RepeatUntilPolicy, RetryPolicy}
import com.monadial.waygrid.common.domain.model.routing.{RouteGraph, RouteNode}
import io.circe.Codec
import io.circe.derivation.Configuration


object CirceInstances:

  given Configuration = Configuration
      .default
      .withDiscriminator("type")

  given Codec[RepeatUntilPolicy] = Codec.AsObject.derivedConfigured
  given Codec[RepeatPolicy] = Codec.AsObject.derivedConfigured
  given Codec[RetryPolicy] = Codec.AsObject.derivedConfigured
  given Codec[DeliveryStrategy] = Codec.AsObject.derivedConfigured
  given Codec[RouteNode] = Codec.AsObject.derived
  given Codec[RouteGraph] = Codec.AsObject.derived
