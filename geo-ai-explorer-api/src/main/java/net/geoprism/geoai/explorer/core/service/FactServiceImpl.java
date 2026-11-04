package net.geoprism.geoai.explorer.core.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.entity.Fact;
import net.geoprism.geoai.explorer.core.repository.FactRepository;

@Service
public class FactServiceImpl extends CrudServiceImpl<Fact, Long> implements FactService
{
  private final FactRepository repository;

  public FactServiceImpl(FactRepository repository)
  {
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
  public Fact update(Fact snippet, Long id)
  {
    Fact entity = repository.findById(id).get();

    if (StringUtils.isNotBlank(snippet.getName()))
    {
      entity.setName(snippet.getName());
    }

    if (StringUtils.isNotBlank(snippet.getDescription()))
    {
      entity.setDescription(snippet.getDescription());
    }

    return repository.save(entity);
  }
}
