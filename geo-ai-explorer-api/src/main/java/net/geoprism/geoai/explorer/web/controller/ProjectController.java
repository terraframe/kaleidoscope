package net.geoprism.geoai.explorer.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.service.ProjectService;

@RestController
@Validated
@RequestMapping("/api/")
public class ProjectController
{
  @Autowired
  private ProjectService service;

  @GetMapping("/projects")
  public List<Project> fetchList()
  {
    return service.fetchList().stream().sorted((a, b) -> b.getCreatedDate().compareTo(a.getCreatedDate())).toList();
  }
}
