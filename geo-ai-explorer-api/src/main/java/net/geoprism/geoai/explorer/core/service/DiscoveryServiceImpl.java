package net.geoprism.geoai.explorer.core.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.entity.Discovery;
import net.geoprism.geoai.explorer.core.repository.DiscoveryRepository;

@Service
public class DiscoveryServiceImpl extends CrudServiceImpl<Discovery, Long> implements DiscoveryService
{
  private final DiscoveryRepository repository;

  public DiscoveryServiceImpl(DiscoveryRepository repository)
  {
    this.repository = repository;
  }

  @Override
  protected DiscoveryRepository repository()
  {
    return repository;
  }

  @Override
  public List<Discovery> fetchListByProject(Long projectId)
  {
    return repository.findAllByProjectId(projectId);
  }

  @Override
  public Discovery update(Discovery snippet, Long id)
  {
    Discovery entity = repository.findById(id).get();

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
