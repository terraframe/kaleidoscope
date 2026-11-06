package net.geoprism.geoai.explorer.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

  /** What it takes to create a project. */
  public record NewProject(@NotBlank @Size(max = 255) String name)
  {
  }

  /**
   * Creates a project, e.g. from the report wizard before it has any facts.
   * Project names are unique, so creating one with a taken name fails.
   */
  @PostMapping("/projects")
  public Project create(@Valid @RequestBody NewProject project)
  {
    return service.save(Project.builder().name(project.name().trim()).build());
  }
}
