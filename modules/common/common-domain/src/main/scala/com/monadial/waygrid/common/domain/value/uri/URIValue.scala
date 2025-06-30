package com.monadial.waygrid.common.domain.value.uri

import com.monadial.waygrid.common.domain.instances.URIInstances.given
import com.monadial.waygrid.common.domain.value.Value

import org.http4s.Uri

abstract class URIValue extends Value[Uri]:
  given IsURI[Type] = derive[IsURI]
