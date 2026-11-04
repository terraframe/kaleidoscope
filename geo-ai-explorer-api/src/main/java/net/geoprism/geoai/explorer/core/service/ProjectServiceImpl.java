package net.geoprism.geoai.explorer.core.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.repository.ProjectRepository;

@Service
public class ProjectServiceImpl extends CrudServiceImpl<Project, Long> implements ProjectService
{
  private final ProjectRepository repository;

  public ProjectServiceImpl(ProjectRepository repository)
  {
    this.repository = repository;
  }

  @Override
  protected CrudRepository<Project, Long> repository()
  {
    return this.repository;
  }

  @Override
  public Project update(Project project, Long id)
  {
    Project entity = repository.findById(id).get();

    if (StringUtils.isNotBlank(project.getName()))
    {
      entity.setName(project.getName());
    }

    return repository.save(entity);
  }
}
