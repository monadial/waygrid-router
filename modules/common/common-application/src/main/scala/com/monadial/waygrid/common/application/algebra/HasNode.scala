package com.monadial.waygrid.common.application.algebra

import com.monadial.waygrid.common.domain.model.node.Node

trait HasNode[F[+_]]:
  def node: Node

object HasNode:
  def apply[F[+_]](using ev: HasNode[F]): HasNode[F] = ev
