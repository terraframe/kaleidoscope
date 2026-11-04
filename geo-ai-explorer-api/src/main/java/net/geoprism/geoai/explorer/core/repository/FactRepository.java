package net.geoprism.geoai.explorer.core.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.entity.Fact;

@Repository
public interface FactRepository extends CrudRepository<Fact, Long>
{
  List<Fact> findAllByProject(Project project);

  List<Fact> findAllByProjectId(Long projectId);
}
