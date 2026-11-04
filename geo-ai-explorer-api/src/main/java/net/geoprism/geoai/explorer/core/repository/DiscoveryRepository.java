package net.geoprism.geoai.explorer.core.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.entity.Discovery;

@Repository
public interface DiscoveryRepository extends CrudRepository<Discovery, Long>
{
  List<Discovery> findAllByProject(Project project);

  List<Discovery> findAllByProjectId(Long projectId);
}
