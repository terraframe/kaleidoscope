package net.geoprism.geoai.explorer.core.service;

import java.util.List;

import net.geoprism.geoai.explorer.core.entity.Fact;

public interface FactService extends CrudService<Fact, Long>
{
  List<Fact> fetchListByProject(Long projectId);
}
