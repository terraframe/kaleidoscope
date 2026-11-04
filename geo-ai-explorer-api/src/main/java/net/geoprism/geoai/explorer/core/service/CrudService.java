package net.geoprism.geoai.explorer.core.service;

import java.util.List;
import java.util.Optional;

public interface CrudService<T, K>
{
  // Save operation
  T save(T entity);

  // Read operation
  List<T> fetchList();

  // Update operation
  T update(T entity, K id);

  // Delete operation
  void deleteById(K id);

  Optional<T> findById(K id);

}
