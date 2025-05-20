package com.monadial.waygrid.common.domain.instances

import java.util.Base64 as JavaBase64

object JavaBridge:
  inline def base64Decoder: String => Array[Byte] = (base64: String) =>
    JavaBase64
      .getDecoder
      .decode(base64)

  inline def base64Encoder: Array[Byte] => String = (t: Array[Byte]) =>
    JavaBase64
      .getEncoder
      .encodeToString(t)
