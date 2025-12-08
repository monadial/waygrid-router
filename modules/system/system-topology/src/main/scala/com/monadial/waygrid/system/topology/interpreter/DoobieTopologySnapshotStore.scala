package com.monadial.waygrid.system.topology.interpreter

import cats.effect.Resource
import com.monadial.waygrid.system.topology.algebra.TopologySnapshotStore
import doobie.hikari.HikariTransactor

object DoobieTopologySnapshotStore:

  def default[F[+_]](xa: HikariTransactor[F]): Resource[F, TopologySnapshotStore[F]] = ???
