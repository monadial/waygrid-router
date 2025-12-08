package com.monadial.waygrid.system.topology.algebra

import com.monadial.waygrid.system.topology.domain.model.topology.TopologySnapshot

trait TopologySnapshotStore[F[+_]]:

  def latest: F[TopologySnapshot]

  def upsert(snapshot: TopologySnapshot): F[Unit]



