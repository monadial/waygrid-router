package com.monadial.waygrid.common.domain.instances

import scodec.bits.ByteVector

object JavaBridge:
  inline def base64Decoder: String => ByteVector = (base64: String) =>
    ByteVector
        .fromBase64Descriptive(base64)
        .fold(x => throw new IllegalArgumentException(x), identity)

  inline def base64Encoder: ByteVector => String = (t: ByteVector) =>
    t.toBase64
