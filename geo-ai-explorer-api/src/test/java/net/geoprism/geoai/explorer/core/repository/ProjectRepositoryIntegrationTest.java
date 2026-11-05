package net.geoprism.geoai.explorer.core.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;

/**
 * Runs against the in-memory H2 database. Each test is transactional and rolls
 * back on completion, so tests do not see each other's data.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
@Transactional
public class ProjectRepositoryIntegrationTest extends MockDataTest
{
  @Autowired
  private ProjectRepository repository;

  private Project newProject(String name)
  {
    Project project = createProject(null);
    project.setName(name);

    return project;
  }

  @Test
  public void testSave_GeneratesId()
  {
    Project saved = repository.save(newProject("Project A"));

    assertNotNull(saved.getId());
    assertEquals("Project A", saved.getName());
  }

  @Test
  public void testFindById()
  {
    Project saved = repository.save(newProject("Project A"));

    Optional<Project> result = repository.findById(saved.getId());

    assertTrue(result.isPresent());
    assertEquals(saved.getId(), result.get().getId());
    assertEquals("Project A", result.get().getName());
  }

  @Test
  public void testFindById_NotFound()
  {
    assertFalse(repository.findById(-1L).isPresent());
  }

  @Test
  public void testFindAll()
  {
    Project a = repository.save(newProject("Project A"));
    Project b = repository.save(newProject("Project B"));

    List<Long> ids = StreamSupport.stream(repository.findAll().spliterator(), false) //
        .map(Project::getId) //
        .toList();

    assertTrue(ids.contains(a.getId()));
    assertTrue(ids.contains(b.getId()));
  }

  @Test
  public void testUpdate()
  {
    Project saved = repository.save(newProject("Project A"));

    saved.setName("Renamed Project");
    repository.save(saved);

    Project result = repository.findById(saved.getId()).orElseThrow();

    assertEquals("Renamed Project", result.getName());
  }

  @Test
  public void testDeleteById()
  {
    Project saved = repository.save(newProject("Project A"));

    repository.deleteById(saved.getId());

    assertFalse(repository.existsById(saved.getId()));
  }

  @Test
  public void testCount()
  {
    long before = repository.count();

    repository.save(newProject("Project A"));
    repository.save(newProject("Project B"));

    assertEquals(before + 2, repository.count());
  }

  @Test
  public void testSave_DuplicateNameFails()
  {
    repository.save(newProject("Duplicate"));

    assertThrows(DataIntegrityViolationException.class, () -> repository.save(newProject("Duplicate")));
  }

  @Test
  public void testSave_NullNameFails()
  {
    assertThrows(DataIntegrityViolationException.class, () -> repository.save(newProject(null)));
  }

  @Autowired
  private EntityManager entityManager;

  /**
   * Writes pending changes and empties the persistence context, so the next
   * read comes from the database rather than the cached entity.
   */
  private void flushAndClear()
  {
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  public void testSave_SetsCreatedDate()
  {
    Instant before = Instant.now().minusSeconds(1);

    Project saved = repository.save(newProject("Project A"));

    assertNotNull(saved.getCreatedDate());

    flushAndClear();

    Instant createdDate = repository.findById(saved.getId()).orElseThrow().getCreatedDate();
    Instant after = Instant.now().plusSeconds(1);

    assertNotNull(createdDate);
    assertTrue(createdDate.isAfter(before), "createdDate " + createdDate + " should be after " + before);
    assertTrue(createdDate.isBefore(after), "createdDate " + createdDate + " should be before " + after);
  }

  @Test
  public void testUpdate_DoesNotChangeCreatedDate()
  {
    Project saved = repository.save(newProject("Project A"));

    flushAndClear();

    Project project = repository.findById(saved.getId()).orElseThrow();
    Instant original = project.getCreatedDate();

    // Change a regular field and try to overwrite the creation timestamp
    project.setName("Renamed");
    project.setCreatedDate(Instant.EPOCH);
    repository.save(project);

    flushAndClear();

    Project reloaded = repository.findById(saved.getId()).orElseThrow();

    assertEquals("Renamed", reloaded.getName());
    assertEquals(original, reloaded.getCreatedDate());
  }
}
