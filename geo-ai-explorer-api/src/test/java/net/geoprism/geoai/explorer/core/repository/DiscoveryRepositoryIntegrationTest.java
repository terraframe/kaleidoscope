package net.geoprism.geoai.explorer.core.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.transaction.Transactional;
import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.entity.Discovery;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;

/**
 * Runs against the in-memory H2 database. Each test is transactional and rolls
 * back on completion, so tests do not see each other's data.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
@Transactional
public class DiscoveryRepositoryIntegrationTest extends MockDataTest
{
  @Autowired
  private DiscoveryRepository repository;

  @Autowired
  private ProjectRepository   projectRepository;

  private Project             project;

  @BeforeEach
  public void setUp()
  {
    Project p = createProject(null);
    p.setName("Snippet Test Project");

    this.project = projectRepository.save(p);
  }

  @Test
  public void testSave_GeneratesId()
  {
    Discovery saved = repository.save(createDiscovery(null, project));

    assertNotNull(saved.getId());
    assertEquals("Original Name", saved.getName());
    assertEquals(project.getId(), saved.getProject().getId());
  }

  @Test
  public void testFindById()
  {
    Discovery saved = repository.save(createDiscovery(null, project));

    Optional<Discovery> result = repository.findById(saved.getId());

    assertTrue(result.isPresent());

    Discovery snippet = result.get();

    assertEquals(saved.getId(), snippet.getId());
    assertEquals("Original Name", snippet.getName());
    assertEquals("Original Description", snippet.getDescription());
    assertEquals("Some text", snippet.getText());
    assertEquals("SELECT * WHERE { ?s ?p ?o }", snippet.getStatements());
    assertEquals(project.getId(), snippet.getProject().getId());
    assertEquals("Snippet Test Project", snippet.getProject().getName());
  }

  @Test
  public void testFindById_NotFound()
  {
    assertFalse(repository.findById(-1L).isPresent());
  }

  @Test
  public void testFindAll()
  {
    Discovery a = repository.save(createDiscovery(null, project));
    Discovery b = repository.save(createDiscovery(null, project));

    List<Long> ids = StreamSupport.stream(repository.findAll().spliterator(), false) //
        .map(Discovery::getId) //
        .toList();

    assertTrue(ids.contains(a.getId()));
    assertTrue(ids.contains(b.getId()));
  }

  @Test
  public void testUpdate()
  {
    Discovery saved = repository.save(createDiscovery(null, project));

    saved.setName("New Name");
    saved.setDescription("New Description");
    repository.save(saved);

    Discovery result = repository.findById(saved.getId()).orElseThrow();

    assertEquals("New Name", result.getName());
    assertEquals("New Description", result.getDescription());
    assertEquals("Some text", result.getText());
  }

  @Test
  public void testDeleteById()
  {
    Discovery saved = repository.save(createDiscovery(null, project));

    repository.deleteById(saved.getId());

    assertFalse(repository.existsById(saved.getId()));
    assertTrue(projectRepository.existsById(project.getId()), "Deleting a snippet must not delete its project");
  }

  @Test
  public void testCount()
  {
    long before = repository.count();

    repository.save(createDiscovery(null, project));
    repository.save(createDiscovery(null, project));

    assertEquals(before + 2, repository.count());
  }

  @Test
  public void testSave_NullProjectFails()
  {
    assertThrows(DataIntegrityViolationException.class, () -> repository.save(createDiscovery(null, null)));
  }

  @Test
  public void testFindAllByProject()
  {
    Project other = createProject(null);
    other.setName("Other Project");
    other = projectRepository.save(other);

    Discovery a = repository.save(createDiscovery(null, project));
    Discovery b = repository.save(createDiscovery(null, project));
    Discovery c = repository.save(createDiscovery(null, other));

    List<Long> ids = repository.findAllByProject(project)
        .stream() //
        .map(Discovery::getId) //
        .toList();

    assertEquals(2, ids.size());
    assertTrue(ids.contains(a.getId()));
    assertTrue(ids.contains(b.getId()));
    assertFalse(ids.contains(c.getId()));

    List<Discovery> otherSnippets = repository.findAllByProject(other);

    assertEquals(1, otherSnippets.size());
    assertEquals(c.getId(), otherSnippets.get(0).getId());
  }

  @Test
  public void testFindAllByProjectId()
  {
    Project other = createProject(null);
    other.setName("Other Project");
    other = projectRepository.save(other);

    Discovery a = repository.save(createDiscovery(null, project));
    Discovery b = repository.save(createDiscovery(null, other));

    List<Discovery> result = repository.findAllByProjectId(project.getId());

    assertEquals(1, result.size());
    assertEquals(a.getId(), result.get(0).getId());
    assertFalse(result.stream().anyMatch(s -> s.getId().equals(b.getId())));
  }

  @Test
  public void testFindAllByProjectId_NotFound()
  {
    assertTrue(repository.findAllByProjectId(-1L).isEmpty());
  }

  @Test
  public void testFindAllByProject_NoSnippets()
  {
    assertTrue(repository.findAllByProject(project).isEmpty());
  }
}
