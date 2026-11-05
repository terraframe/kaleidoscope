package net.geoprism.geoai.explorer.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import net.geoprism.geoai.explorer.core.dto.FactDTO;
import net.geoprism.geoai.explorer.core.entity.Fact;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;
import net.geoprism.geoai.explorer.core.service.FactService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Web-layer tests for {@link FactController}. Uses a standalone MockMvc setup,
 * so request mapping, JSON conversion and {@code @Valid} validation are real,
 * while {@link FactService} is mocked and no Spring context or security filters
 * are loaded.
 */
@ExtendWith(MockitoExtension.class)
public class FactControllerTest extends MockDataTest
{
  private static final String BASE_URL = "/api/fact/facts";

  @Mock
  private FactService         service;

  @InjectMocks
  private FactController      controller;

  private MockMvc             mockMvc;

  @BeforeEach
  public void setUp()
  {
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  private String toJson(Object value)
  {
    return JsonMapper.shared().writeValueAsString(value);
  }

  /*
   * POST /api/fact/facts
   */

  @Test
  public void testSave_WithProjectId() throws Exception
  {
    FactDTO request = FactDTO.builder() //
        .name("Fact A") //
        .description("Description A") //
        .text("Some text") //
        .statements("SELECT * WHERE { ?s ?p ?o }") //
        .projectId(5L) //
        .build();

    FactDTO saved = FactDTO.builder() //
        .id(10L) //
        .name("Fact A") //
        .description("Description A") //
        .text("Some text") //
        .statements("SELECT * WHERE { ?s ?p ?o }") //
        .projectId(5L) //
        .projectName("Project A") //
        .build();

    when(service.save(any(FactDTO.class))).thenReturn(saved);

    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(toJson(request))) //
        .andExpect(status().isOk()) //
        .andExpect(jsonPath("$.id").value(10)) //
        .andExpect(jsonPath("$.name").value("Fact A")) //
        .andExpect(jsonPath("$.description").value("Description A")) //
        .andExpect(jsonPath("$.projectId").value(5)) //
        .andExpect(jsonPath("$.projectName").value("Project A")) //
        .andExpect(jsonPath("$.projectSpecified").doesNotExist());

    ArgumentCaptor<FactDTO> captor = ArgumentCaptor.forClass(FactDTO.class);
    verify(service).save(captor.capture());

    assertEquals("Fact A", captor.getValue().getName());
    assertEquals(5L, captor.getValue().getProjectId());
    assertNull(captor.getValue().getId());
  }

  @Test
  public void testSave_WithProjectNameOnly() throws Exception
  {
    FactDTO request = FactDTO.builder() //
        .name("Fact A") //
        .description("Description A") //
        .projectName("New Project") //
        .build();

    when(service.save(any(FactDTO.class))).thenAnswer(invocation -> {
      FactDTO dto = invocation.getArgument(0);
      dto.setId(10L);
      dto.setProjectId(7L);
      return dto;
    });

    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(toJson(request))) //
        .andExpect(status().isOk()) //
        .andExpect(jsonPath("$.id").value(10)) //
        .andExpect(jsonPath("$.projectId").value(7)) //
        .andExpect(jsonPath("$.projectName").value("New Project"));

    verify(service).save(any(FactDTO.class));
  }

  @Test
  public void testSave_WithoutProjectIdOrName_IsRejected() throws Exception
  {
    FactDTO request = FactDTO.builder() //
        .name("Fact A") //
        .description("Description A") //
        .build();

    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(toJson(request))) //
        .andExpect(status().isBadRequest());

    verify(service, never()).save(any(FactDTO.class));
  }

  @Test
  public void testSave_WithBlankProjectName_IsRejected() throws Exception
  {
    FactDTO request = FactDTO.builder() //
        .name("Fact A") //
        .description("Description A") //
        .projectName("   ") //
        .build();

    mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(toJson(request))) //
        .andExpect(status().isBadRequest());

    verify(service, never()).save(any(FactDTO.class));
  }

  /*
   * GET /api/fact/facts
   */

  @Test
  public void testFetchList() throws Exception
  {
    Project project = createProject(3L, "Project A");

    Fact first = createFact(1L, project);
    first.setName("Fact 1");

    Fact second = createFact(2L, project);
    second.setName("Fact 2");

    when(service.fetchListByProject(3L)).thenReturn(List.of(first, second));

    // The service is mocked, so stub the entity -> DTO conversion as well
    when(service.toDTO(any(Fact.class))).thenAnswer(invocation -> {
      Fact fact = invocation.getArgument(0);

      return FactDTO.builder() //
          .id(fact.getId()) //
          .name(fact.getName()) //
          .description(fact.getDescription()) //
          .text(fact.getText()) //
          .statements(fact.getStatements()) //
          .projectId(fact.getProject().getId()) //
          .projectName(fact.getProject().getName()) //
          .build();
    });

    mockMvc.perform(get(BASE_URL + "/3")) //
        .andExpect(status().isOk()) //
        .andExpect(jsonPath("$.length()").value(2)) //
        .andExpect(jsonPath("$[0].id").value(1)) //
        .andExpect(jsonPath("$[0].name").value("Fact 1")) //
        .andExpect(jsonPath("$[0].description").value("Original Description")) //
        .andExpect(jsonPath("$[0].text").value("Some text")) //
        .andExpect(jsonPath("$[0].statements").value("SELECT * WHERE { ?s ?p ?o }")) //
        .andExpect(jsonPath("$[0].projectId").value(3)) //
        .andExpect(jsonPath("$[0].projectName").value("Project A")) //
        .andExpect(jsonPath("$[1].id").value(2)) //
        .andExpect(jsonPath("$[1].name").value("Fact 2")) //
        .andExpect(jsonPath("$[1].projectId").value(3)) //
        // DTOs flatten the project to id/name rather than nesting the entity
        .andExpect(jsonPath("$[0].project").doesNotExist()) //
        .andExpect(jsonPath("$[0].projectSpecified").doesNotExist());

    verify(service).fetchListByProject(3L);
    verify(service).toDTO(first);
    verify(service).toDTO(second);
  }

  @Test
  public void testFetchList_Empty() throws Exception
  {
    when(service.fetchListByProject(anyLong())).thenReturn(List.of());

    mockMvc.perform(get(BASE_URL + "/1")) //
        .andExpect(status().isOk()) //
        .andExpect(jsonPath("$.length()").value(0));
  }

  /*
   * PUT /api/fact/facts/{id}
   */

  @Test
  public void testUpdate() throws Exception
  {
    Fact changes = Fact.builder().name("New Name").description("New Description").build();

    Fact updated = createFact(5L);
    updated.setName("New Name");
    updated.setDescription("New Description");

    when(service.update(any(Fact.class), eq(5L))).thenReturn(updated);

    mockMvc.perform(put(BASE_URL + "/5").contentType(MediaType.APPLICATION_JSON).content(toJson(changes))) //
        .andExpect(status().isOk()) //
        .andExpect(jsonPath("$.id").value(5)) //
        .andExpect(jsonPath("$.name").value("New Name")) //
        .andExpect(jsonPath("$.description").value("New Description")) //
        .andExpect(jsonPath("$.text").value("Some text"));

    ArgumentCaptor<Fact> captor = ArgumentCaptor.forClass(Fact.class);
    verify(service).update(captor.capture(), eq(5L));

    assertEquals("New Name", captor.getValue().getName());
    assertEquals("New Description", captor.getValue().getDescription());
  }

  /*
   * DELETE /api/fact/facts/{id}
   */

  @Test
  public void testDeleteById() throws Exception
  {
    mockMvc.perform(delete(BASE_URL + "/5")) //
        .andExpect(status().isOk()) //
        .andExpect(content().string("Deleted Successfully"));

    verify(service).deleteById(5L);
  }
}
