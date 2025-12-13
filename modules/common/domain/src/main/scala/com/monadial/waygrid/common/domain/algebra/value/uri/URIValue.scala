package com.monadial.waygrid.common.domain.algebra.value.uri

import com.monadial.waygrid.common.domain.algebra.value.Value
import com.monadial.waygrid.common.domain.instances.URIInstances.given

import org.http4s.Uri

abstract class URIValue extends Value[Uri]:
  given IsURI[Type] = derive[IsURI]
