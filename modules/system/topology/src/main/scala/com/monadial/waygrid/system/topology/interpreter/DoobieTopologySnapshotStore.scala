package com.monadial.waygrid.system.topology.interpreter

import com.monadial.waygrid.system.topology.algebra.TopologySnapshotStore

import cats.effect.Resource
import doobie.hikari.HikariTransactor

object DoobieTopologySnapshotStore:

  def default[F[+_]](xa: HikariTransactor[F]): Resource[F, TopologySnapshotStore[F]] = ???
