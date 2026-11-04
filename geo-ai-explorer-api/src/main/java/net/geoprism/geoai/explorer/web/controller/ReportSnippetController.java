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

import net.geoprism.geoai.explorer.core.entity.Discovery;
import net.geoprism.geoai.explorer.core.service.DiscoveryService;

@RestController
@Validated
@RequestMapping("/api/snippet")
public class ReportSnippetController
{
  @Autowired
  private DiscoveryService service;

  @PostMapping("/snippets")
  public Discovery save(@RequestBody Discovery snippet)
  {
    return service.save(snippet);
  }

  @GetMapping("/snippets")
  public List<Discovery> fetchList()
  {
    return service.fetchList();
  }

  @PutMapping("/snippets/{id}")
  public Discovery update(@RequestBody Discovery snippet, @PathVariable("id") Long id)
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
