package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.domain.model.node.Node

trait ThisNode[F[+_]]:
  def get: F[Node]

object ThisNode:
  def apply[F[+_]](using ev: ThisNode[F]): ThisNode[F] = ev
