package net.geoprism.geoai.explorer.core.service;

import static org.mockito.ArgumentMatchers.anyLong;
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
import net.geoprism.geoai.explorer.core.entity.Discovery;
import net.geoprism.geoai.explorer.core.entity.Project;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class ReportGenerationIntegrationTest extends MockDataTest
{

  @Autowired
  private BedrockService   service;

  @MockitoBean
  private ProjectService   projectService;

  @MockitoBean
  private DiscoveryService discoveryService;

  @BeforeEach
  public void setUp()
  {
    Project project = createProject(1L, "Upper Guadalupe River");

    Discovery cost = Discovery.builder() //
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

    Discovery benefits = Discovery.builder() //
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

    when(projectService.fetchList()).thenReturn(List.of(project));

    when(discoveryService.fetchListByProject(anyLong())).thenReturn(List.of(cost, benefits));
    when(discoveryService.findById(1L)).thenReturn(Optional.of(cost));
    when(discoveryService.findById(2L)).thenReturn(Optional.of(benefits));
  }

  @Test
  public void testReportGeneration() throws InterruptedException, ExecutionException, TimeoutException
  {
    String input = """
            Generate a report using template 'PLAN_EVAL' for project 1 where discoveries 1 and 2
            are used in the National Economic Development (NED) Analysis section
        """;

    String sessionId = UUID.randomUUID().toString();

    String report = service.report(sessionId, input);

    System.out.println(report);
  }

}
