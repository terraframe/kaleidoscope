package net.geoprism.geoai.explorer.core.service;

import java.util.List;

import net.geoprism.geoai.explorer.core.entity.Discovery;

public interface DiscoveryService extends CrudService<Discovery, Long>
{
  List<Discovery> fetchListByProject(Long projectId);
}
