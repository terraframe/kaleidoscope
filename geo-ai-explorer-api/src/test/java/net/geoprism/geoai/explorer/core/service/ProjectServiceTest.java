package net.geoprism.geoai.explorer.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;
import net.geoprism.geoai.explorer.core.repository.ProjectRepository;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class ProjectServiceTest extends MockDataTest
{
  @Autowired
  private ProjectService    service;

  @MockitoBean
  private ProjectRepository repository;

  @Test
  public void testSave()
  {
    Project snippet = createProject(null);
    Project saved = createProject(1L);

    when(repository.save(snippet)).thenReturn(saved);

    Project result = service.save(snippet);

    assertSame(saved, result);
    verify(repository).save(snippet);
  }

  @Test
  public void testFetchList()
  {
    List<Project> snippets = List.of(createProject(1L), createProject(2L));

    when(repository.findAll()).thenReturn(snippets);

    List<Project> result = service.fetchList();

    assertEquals(2, result.size());
    assertEquals(1L, result.get(0).getId());
    assertEquals(2L, result.get(1).getId());
  }

  @Test
  public void testFetchList_Empty()
  {
    when(repository.findAll()).thenReturn(List.of());

    assertTrue(service.fetchList().isEmpty());
  }

  @Test
  public void testUpdate_Name()
  {
    Project existing = createProject(1L);

    when(repository.findById(1L)).thenReturn(Optional.of(existing));
    when(repository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Project changes = Project.builder().name("New Name").build();

    Project result = service.update(changes, 1L);

    assertEquals(1L, result.getId());
    assertEquals("New Name", result.getName());

    ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
    verify(repository).save(captor.capture());
    assertSame(existing, captor.getValue());
  }

  @Test
  public void testUpdate_BlankFieldsAreIgnored()
  {
    Project existing = createProject(1L);

    when(repository.findById(1L)).thenReturn(Optional.of(existing));
    when(repository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Project changes = Project.builder().name("  ").build();

    Project result = service.update(changes, 1L);

    assertEquals(existing.getName(), result.getName());
  }

  @Test
  public void testUpdate_NotFound()
  {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    Project changes = Project.builder().name("New Name").build();

    assertThrows(NoSuchElementException.class, () -> service.update(changes, 99L));
    verify(repository, never()).save(any(Project.class));
  }

  @Test
  public void testDeleteById()
  {
    service.deleteById(1L);

    verify(repository).deleteById(1L);
  }
}
