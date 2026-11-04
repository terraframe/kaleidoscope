package net.geoprism.geoai.explorer.core.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public abstract class CrudServiceImpl<T, K> implements CrudService<T, K>
{
  protected abstract CrudRepository<T, K> repository();

  @Override
  public T save(T snippet)
  {
    return this.repository().save(snippet);
  }

  @Override
  public Optional<T> findById(K id)
  {
    return this.repository().findById(id);
  }

  @Override
  public List<T> fetchList()
  {
    return (List<T>) this.repository().findAll();
  }

  @Override
  public void deleteById(K id)
  {
    this.repository().deleteById(id);
  }

}
