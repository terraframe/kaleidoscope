package net.geoprism.geoai.explorer.core.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.dto.FactDTO;
import net.geoprism.geoai.explorer.core.entity.Fact;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.repository.FactRepository;
import tools.jackson.databind.json.JsonMapper;

@Service
public class FactServiceImpl extends CrudServiceImpl<Fact, Long> implements FactService
{
  private final ProjectService projectService;

  private final FactRepository repository;

  public FactServiceImpl(ProjectService projectService, FactRepository repository)
  {
    this.projectService = projectService;
    this.repository = repository;
  }

  @Override
  protected FactRepository repository()
  {
    return repository;
  }

  @Override
  public List<Fact> fetchListByProject(Long projectId)
  {
    return repository.findAllByProjectId(projectId);
  }

  @Override
  public Fact update(Fact fact, Long id)
  {
    Fact entity = getOrThrow(id);

    if (StringUtils.isNotBlank(fact.getName()))
    {
      entity.setName(fact.getName());
    }

    if (StringUtils.isNotBlank(fact.getDescription()))
    {
      entity.setDescription(fact.getDescription());
    }

    return repository.save(entity);
  }

  @Override
  public FactDTO save(FactDTO dto)
  {
    Project project = ( dto.getProjectId() != null ) ? //
        this.projectService.getOrThrow(dto.getProjectId()) : //
        this.projectService.save(Project.builder().name(dto.getProjectName()).build());

    Fact entity = ( dto.getId() != null ) ? //
        this.getOrThrow(dto.getId()) : //
        Fact.builder().project(project).build();

    entity.setDescription(dto.getDescription());
    entity.setName(dto.getName());
    entity.setStatements(JsonMapper.shared().writeValueAsString(dto.getStatements()));
    entity.setText(dto.getText());
    entity.setMapStatement(dto.getMapStatement());

    return toDTO(repository.save(entity), project);
  }

  @Override
  public FactDTO toDTO(Fact entity)
  {
    return toDTO(entity, entity.getProject());
  }

  protected FactDTO toDTO(Fact entity, Project project)
  {
    return FactDTO.builder() //
        .id(entity.getId()) //
        .name(entity.getName()) //
        .description(entity.getDescription()) //
        .statements(JsonMapper.shared().readerForListOf(String.class).readValue(entity.getStatements())) //
        .text(entity.getText()) //
        .mapStatement(entity.getMapStatement()) //
        .projectId(project.getId()) //
        .projectName(project.getName()) //
        .build();
  }
}
