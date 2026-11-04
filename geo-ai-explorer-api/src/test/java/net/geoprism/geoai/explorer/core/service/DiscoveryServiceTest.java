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
import net.geoprism.geoai.explorer.core.entity.Discovery;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;
import net.geoprism.geoai.explorer.core.repository.DiscoveryRepository;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class DiscoveryServiceTest extends MockDataTest
{
  @Autowired
  private DiscoveryService    service;

  @MockitoBean
  private DiscoveryRepository repository;

  @Test
  public void testSave()
  {
    Discovery discovery = createDiscovery(null);
    Discovery saved = createDiscovery(1L);

    when(repository.save(discovery)).thenReturn(saved);

    Discovery result = service.save(discovery);

    assertSame(saved, result);
    verify(repository).save(discovery);
  }

  @Test
  public void testFetchList()
  {
    List<Discovery> discoverys = List.of(createDiscovery(1L), createDiscovery(2L));

    when(repository.findAll()).thenReturn(discoverys);

    List<Discovery> result = service.fetchList();

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
  public void testFetchListByProject()
  {
    Project project = createProject(1L);
    List<Discovery> discoveries = List.of(createDiscovery(1L, project), createDiscovery(2L, project));

    when(repository.findAllByProjectId(1L)).thenReturn(discoveries);

    List<Discovery> result = service.fetchListByProject(1L);

    assertEquals(2, result.size());
    assertEquals(1L, result.get(0).getId());
    assertEquals(2L, result.get(1).getId());
    assertTrue(result.stream().allMatch(s -> s.getProject() == project));
    verify(repository).findAllByProjectId(1L);
  }

  @Test
  public void testFetchListByProject_Empty()
  {
    when(repository.findAllByProjectId(2L)).thenReturn(List.of());

    assertTrue(service.fetchListByProject(2L).isEmpty());
  }

  @Test
  public void testUpdate_NameAndDescription()
  {
    Discovery existing = createDiscovery(1L);

    when(repository.findById(1L)).thenReturn(Optional.of(existing));
    when(repository.save(any(Discovery.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Discovery changes = Discovery.builder().name("New Name").description("New Description").build();

    Discovery result = service.update(changes, 1L);

    assertEquals(1L, result.getId());
    assertEquals("New Name", result.getName());
    assertEquals("New Description", result.getDescription());
    assertEquals("Some text", result.getText());
    assertEquals("SELECT * WHERE { ?s ?p ?o }", result.getStatements());

    ArgumentCaptor<Discovery> captor = ArgumentCaptor.forClass(Discovery.class);
    verify(repository).save(captor.capture());
    assertSame(existing, captor.getValue());
  }

  @Test
  public void testUpdate_BlankFieldsAreIgnored()
  {
    Discovery existing = createDiscovery(1L);

    when(repository.findById(1L)).thenReturn(Optional.of(existing));
    when(repository.save(any(Discovery.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Discovery changes = Discovery.builder().name("  ").description(null).build();

    Discovery result = service.update(changes, 1L);

    assertEquals("Original Name", result.getName());
    assertEquals("Original Description", result.getDescription());
  }

  @Test
  public void testUpdate_NotFound()
  {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    Discovery changes = Discovery.builder().name("New Name").build();

    assertThrows(NoSuchElementException.class, () -> service.update(changes, 99L));
    verify(repository, never()).save(any(Discovery.class));
  }

  @Test
  public void testDeleteById()
  {
    service.deleteById(1L);

    verify(repository).deleteById(1L);
  }
}
