package net.geoprism.geoai.explorer.core.mock;

import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.entity.Fact;

public abstract class MockDataTest
{
  protected Project createProject(Long id)
  {
    return createProject(id, "Test Project");
  }

  protected Project createProject(Long id, String name)
  {
    return Project.builder() //
        .id(id) //
        .name(name) //
        .build();
  }

  protected Fact createFact(Long id)
  {
    return createFact(id, createProject(1L));
  }

  protected Fact createFact(Long id, Project project)
  {
    return Fact.builder() //
        .id(id) //
        .name("Original Name") //
        .description("Original Description") //
        .text("Some text") //
        .statements("SELECT * WHERE { ?s ?p ?o }") //
        .project(project) //
        .build();
  }

}
