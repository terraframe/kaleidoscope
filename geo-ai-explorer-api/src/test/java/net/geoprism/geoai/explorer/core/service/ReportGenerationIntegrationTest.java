package net.geoprism.geoai.explorer.core.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.entity.Fact;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class ReportGenerationIntegrationTest extends MockDataTest
{

  @Autowired
  private BedrockService        service;

  @MockitoBean
  private ProjectService        projectService;

  @MockitoBean
  private FactService           factService;

  @MockitoBean
  private ReportTemplateService templateService;

  @BeforeEach
  public void setUp()
  {
    Project project = createProject(1L, "Upper Guadalupe River");

    Fact cost = Fact.builder() //
        .id(1L) //
        .project(project) //
        .name("Average annual costs of scenarios") //
        .description("Comparison of the average annual cost to implement each scenario") //
        .text("""
            2b – Modified Valley View Plan (NED Plan from 1998 FS/EIS/EIR) - $14.0M
            3b – Modified Bypass Plan (previous authorized plan, partially constructed) - $21.2M
            4 - Nonstructural Plan - $9.9M
            7 – Low Scope Plan - $5.5M
            8b – Combination Plan - $6.4M
            """)
        .build();

    Fact benefits = Fact.builder() //
        .id(2L) //
        .project(project) //
        .name("Average annual benefits of scenarios") //
        .description("Comparison of the average annual benefits of implementing each scenario") //
        .text("""
            2b – Modified Valley View Plan (NED Plan from 1998 FS/EIS/EIR) - $22.1M
            3b – Modified Bypass Plan (previous authorized plan, partially constructed) - $21.9M
            4 - Nonstructural Plan - $9.2M
            7 – Low Scope Plan - $20.1M
            8b – Combination Plan - $21.6M
            """)
        .build();

    Fact output = Fact.builder() //
        .id(3L) //
        .project(project) //
        .name("Estimated cost output of scenarios") //
        .description("Comparison of the estimated cost output for each scenario") //
        .text("""
            2b – Modified Valley View Plan (NED Plan from 1998 FS/EIS/EIR) - $233.7M
            3b – Modified Bypass Plan (previous authorized plan, partially constructed) - $373.1M
            4 - Nonstructural Plan - $304.6M
            7 – Low Scope Plan - $59.4M
            8b – Combination Plan - $83.9M
            """)
        .build();

    Fact jobs = Fact.builder() //
        .id(4L) //
        .project(project) //
        .name("Estimated job creation of scenarios") //
        .description("Comparison of the estimated job creation for each scenario") //
        .text("""
            2b – Modified Valley View Plan (NED Plan from 1998 FS/EIS/EIR) - 1543.5
            3b – Modified Bypass Plan (previous authorized plan, partially constructed) - 2463.3
            4 - Nonstructural Plan - 2011
            7 – Low Scope Plan - 392.8
            8b – Combination Plan - 554.5
            """)
        .build();

    when(projectService.fetchList()).thenReturn(List.of(project));

    when(factService.fetchListByProject(anyLong())).thenReturn(List.of(cost, benefits, output, jobs));
    when(factService.findById(1L)).thenReturn(Optional.of(cost));
    when(factService.findById(2L)).thenReturn(Optional.of(benefits));
    when(factService.findById(3L)).thenReturn(Optional.of(output));
    when(factService.findById(4L)).thenReturn(Optional.of(jobs));

    when(templateService.getAll()).thenReturn(List.of(createReportTemplate()));
    when(templateService.get(anyString())).thenReturn(createReportTemplate());
  }

  @Test
  public void testReportGeneration() throws InterruptedException, ExecutionException, TimeoutException
  {
    String input = """
            Generate a report using template 'PLAN_EVAL' for project 1.  Use discoveries 1 and 2
            in the National Economic Development (NED) Analysis section. Use discoveries
            3 and 4 in the Regional Economic Development (RED) Analysis section.
        """;

    String sessionId = UUID.randomUUID().toString();

    String report = service.report(sessionId, input);

    System.out.println(report);
  }

  @Test
  public void testFactSuggestions() throws InterruptedException, ExecutionException, TimeoutException
  {
    String input = """
        Generate a report using template 'PLAN_EVAL' for project 1.  What facts are most appropriate for National Economic Development (NED) Analysis section?""";

    String sessionId = UUID.randomUUID().toString();

    String response = service.report(sessionId, input);

    System.out.println(response);

    assertTrue(response.toLowerCase().contains("**1") || response.toLowerCase().contains("fact 1"), "Failed assumption with response" + response);
    assertTrue(response.toLowerCase().contains("**2") || response.toLowerCase().contains("fact 2"), "Failed assumption with response" + response);
  }

}
