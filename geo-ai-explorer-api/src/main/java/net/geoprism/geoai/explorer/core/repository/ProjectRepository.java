package net.geoprism.geoai.explorer.core.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import net.geoprism.geoai.explorer.core.entity.Project;

@Repository
public interface ProjectRepository extends CrudRepository<Project, Long>
{

}