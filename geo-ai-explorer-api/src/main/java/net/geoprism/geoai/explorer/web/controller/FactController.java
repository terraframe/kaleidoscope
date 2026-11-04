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

import net.geoprism.geoai.explorer.core.entity.Fact;
import net.geoprism.geoai.explorer.core.service.FactService;

@RestController
@Validated
@RequestMapping("/api/snippet")
public class FactController
{
  @Autowired
  private FactService service;

  @PostMapping("/snippets")
  public Fact save(@RequestBody Fact snippet)
  {
    return service.save(snippet);
  }

  @GetMapping("/snippets")
  public List<Fact> fetchList()
  {
    return service.fetchList();
  }

  @PutMapping("/snippets/{id}")
  public Fact update(@RequestBody Fact snippet, @PathVariable("id") Long id)
  {
    return service.update(snippet, id);
  }

  // Delete operation
  @DeleteMapping("/snippets/{id}")
  public String deleteById(@PathVariable("id") Long id)
  {
    service.deleteById(id);

    return "Deleted Successfully";
  }
}
