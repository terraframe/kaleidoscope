package net.geoprism.geoai.explorer.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.geoprism.geoai.explorer.core.dto.FactDTO;
import net.geoprism.geoai.explorer.core.entity.Fact;
import net.geoprism.geoai.explorer.core.service.FactService;

@RestController
@Validated
@RequestMapping("/api/fact")
public class FactController
{
  @Autowired
  private FactService service;

  @PostMapping("/facts")
  public FactDTO save(@Valid @RequestBody FactDTO fact)
  {
    return service.save(fact);
  }

  @GetMapping("/facts/{projectId}")
  public List<FactDTO> fetchList(@PathVariable("projectId") Long projectId)
  {
    return service.fetchListByProject(projectId).stream().map(service::toDTO).toList();
  }

  @PutMapping("/facts/{id}")
  public Fact update(@RequestBody Fact fact, @PathVariable("id") Long id)
  {
    return service.update(fact, id);
  }

  // Delete operation
  @DeleteMapping("/facts/{id}")
  public String deleteById(@PathVariable("id") Long id)
  {
    service.deleteById(id);

    return "Deleted Successfully";
  }
}
