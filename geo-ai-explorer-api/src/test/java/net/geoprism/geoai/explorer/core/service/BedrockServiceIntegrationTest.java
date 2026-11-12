package net.geoprism.geoai.explorer.core.service;

import static net.geoprism.geoai.explorer.core.service.ChatAgentAssertions.assertNoProblems;
import static net.geoprism.geoai.explorer.core.service.ChatAgentAssertions.checkLocationSetWhenAmbiguous;
import static net.geoprism.geoai.explorer.core.service.ChatAgentAssertions.checkMapStatementMatchesMapIt;
import static net.geoprism.geoai.explorer.core.service.ChatAgentAssertions.checkNoGeometryInExecutedQueries;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.mock.MockDataTest;
import net.geoprism.geoai.explorer.core.model.Message;
import net.geoprism.geoai.explorer.core.model.NameResolutionResult;
import net.geoprism.geoai.explorer.core.service.search.BasicSearchService;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class BedrockServiceIntegrationTest extends MockDataTest
{

  @Autowired
  private BedrockService     service;

  @MockitoBean
  private BasicSearchService searchService;

  @MockitoBean
  private GraphQueryService  queryService;

  @BeforeEach
  public void setUp()
  {
    when(searchService.nameResolution(anyString())).thenReturn(List.of( //
        NameResolutionResult.builder() //
            .uri("https://localhost/graph#Structure-2012X")
            .type("Structure")
            .label("849W84F9+M6C-4-5-3-4")
            .code("2012X")
            .build() //
    ));

    when(queryService.select(anyString())).thenReturn(List.of(Map.of("value", 431826)));
  }

  @Test
  public void testPrompt_NameResolutionAndFollowUp() throws InterruptedException, ExecutionException, TimeoutException
  {
    String sessionId = UUID.randomUUID().toString();

    Message message = service.prompt(sessionId, "Give me the value of a structure with the name 849W84F9+M6C-4-5-3-4");

    assertNoProblems(checkLocationSetWhenAmbiguous(message));

    System.out.println(message.getContent());

    Assert.assertTrue(message.getContent().trim().length() > 0);
    Assert.assertTrue(message.getContent().contains("431,826"));
    Assert.assertFalse(message.getContent().contains("<location><label>"));
    Assert.assertFalse(message.getContent().contains("<name>"));
    Assert.assertFalse(message.getAmbiguous());
    Assert.assertEquals(sessionId, message.getSessionId());

    List<String> statements = message.getStatements();

    Assert.assertEquals(1, statements.size());

    System.out.println(statements.get(0));

    assertNoProblems(checkNoGeometryInExecutedQueries(statements));
    Assert.assertTrue("Failed with message: " + message.getMapStatement(), message.getMappable());
    assertNoProblems(checkMapStatementMatchesMapIt(message));
  }

  @Test
  public void testPrompt_Mappable() throws InterruptedException, ExecutionException, TimeoutException
  {
    when(searchService.nameResolution(anyString())).thenReturn(List.of( //
        NameResolutionResult.builder() //
            .uri("https://localhost/graph#FloodScenario-1")
            .type("FloodScenario")
            .label("No Mitigation")
            .code("1")
            .build() //
    ));

    when(queryService.select(anyString())).thenReturn(List.of(Map.of("total", 200)));

    String sessionId = UUID.randomUUID().toString();

    Message message = service.prompt(sessionId, "How many land parcels at risk with the no mitigation flood scenario?");

    assertNoProblems(checkLocationSetWhenAmbiguous(message));

    Assert.assertTrue(message.getContent().trim().length() > 0);
    Assert.assertTrue(message.getContent().contains("200"));
    Assert.assertFalse(message.getAmbiguous());
    Assert.assertTrue("Failed with message: " + message.getContent(), message.getMappable());

    assertNoProblems(checkNoGeometryInExecutedQueries(message.getStatements()));
    assertNoProblems(checkMapStatementMatchesMapIt(message, "LandParcel", "HasFloodRisk"));
  }

  @Test
  public void testPrompt_AmbiguousName() throws InterruptedException, ExecutionException, TimeoutException
  {
    when(searchService.nameResolution(anyString())).thenReturn(List.of( //
        NameResolutionResult.builder() //
            .uri("https://localhost/graph#Structure-CELRN_CL_ND_MEL_25")
            .type("Structure")
            .label("structure_25")
            .code("CELRN_CL_ND_MEL_25")
            .build(), //
        NameResolutionResult.builder() //
            .uri("https://localhost/graph#Structure-CEMVK_RR_03_ONE_25")
            .type("Structure")
            .label("structure_25")
            .code("CEMVK_RR_03_ONE_25")
            .build(), //
        NameResolutionResult.builder() //
            .uri("https://localhost/graph#Structure-CERON_RR_03_QRD_25")
            .type("Structure")
            .label("structure_25")
            .code("CERON_RR_03_QRD_25")
            .build() //

    ));

    Message message = service.prompt(UUID.randomUUID().toString(), "what is the total population of a structure with the name structure_25?");

    assertNoProblems(checkLocationSetWhenAmbiguous(message));

    Assert.assertTrue("Failed with message: " + message.getContent(), message.getAmbiguous());
    Assert.assertTrue("Ambiguous response did not set a location", StringUtils.isNotBlank(message.getLocation()));
    Assert.assertEquals("structure_25", message.getLocation());

    // Nothing to audit while the user is still choosing between matches
    Assert.assertNull(message.getMapStatement());
    assertNoProblems(checkNoGeometryInExecutedQueries(message.getStatements()));
  }

  @Test
  public void testPrompt_PopulationAggregate() throws InterruptedException, ExecutionException, TimeoutException
  {
    when(queryService.select(anyString())).thenReturn(List.of(Map.of("totalPopulation", 5432)));

    Message message = service.prompt(UUID.randomUUID().toString(), "How many people are in the project area with a code of '1'?");

    assertNoProblems(checkLocationSetWhenAmbiguous(message));

    System.out.println(message.getContent());
    System.out.println(message.getMapStatement());

    Assert.assertTrue("Failed with message: " + message.getContent(), message.getContent().contains("5,432") || message.getContent().contains("5432"));
    Assert.assertFalse(message.getAmbiguous());
    Assert.assertFalse(message.getStatements().isEmpty());
    Assert.assertTrue("Failed with message: " + message.getContent(), message.getMappable());

    // The executed aggregate must not touch geometry; the audit statement
    // returns the summed structures
    assertNoProblems(checkNoGeometryInExecutedQueries(message.getStatements()));
    assertNoProblems(checkMapStatementMatchesMapIt(message, "Structure", "pop2pmu65"));
  }

  @Test
  public void testPrompt_ScenarioComparison() throws InterruptedException, ExecutionException, TimeoutException
  {
    when(queryService.select(anyString())).thenReturn(List.of(Map.of("totalValue", 1000)));

    Message message = service.prompt(UUID.randomUUID().toString(), "What is the total value of structures at risk with the no mitigation scenario versus the combo plan scenario?");

    assertNoProblems(checkLocationSetWhenAmbiguous(message));

    System.out.println(message.getContent());
    System.out.println();

    Assert.assertTrue(message.getContent().trim().length() > 0);
    Assert.assertFalse(message.getAmbiguous());

    // Unchanged behavior: comparisons across several queries are not flagged
    // mapIt
    Assert.assertFalse("Failed with map statement: " + message.getMapStatement(), message.getMappable());

    // ...so no map statement is expected either
    assertNoProblems(checkNoGeometryInExecutedQueries(message.getStatements()));
    assertNoProblems(checkMapStatementMatchesMapIt(message));
  }

  @Test
  public void testPrompt_NoQueryNeeded() throws InterruptedException, ExecutionException, TimeoutException
  {
    Message message = service.prompt(UUID.randomUUID().toString(), "Hello! What kinds of questions can you help me with?");

    assertNoProblems(checkLocationSetWhenAmbiguous(message));

    System.out.println(message.getContent());

    Assert.assertTrue(message.getContent().trim().length() > 0);
    Assert.assertTrue("Unexpected queries: " + message.getStatements(), message.getStatements().isEmpty());
    Assert.assertNull(message.getMapStatement());
    Assert.assertFalse(message.getMappable());
  }

  @Test
  public void testPrompt_ExplicitGeometryRequest() throws InterruptedException, ExecutionException, TimeoutException
  {
    when(queryService.select(anyString())).thenReturn(List.of(Map.of("wkt", "POINT (-122.03 37.22)")));

    Message message = service.prompt(UUID.randomUUID().toString(), "What is the WKT geometry of the structure with the code 2012X?");

    assertNoProblems(checkLocationSetWhenAmbiguous(message));

    System.out.println(message.getContent());
    message.getStatements().forEach(System.out::println);

    Assert.assertTrue(message.getContent().trim().length() > 0);
    Assert.assertFalse(message.getStatements().isEmpty());

    // Geometry is allowed here, but only for the specific object the user asked
    // about
    for (String statement : message.getStatements())
    {
      Assert.assertTrue("Geometry query was not restricted to the requested object:\n" + statement, statement.contains("2012X"));
      Assert.assertFalse("Geometry query used an aggregate:\n" + statement, statement.toUpperCase().matches("(?s).*\\b(SUM|COUNT|AVG)\\s*\\(.*"));
    }

    // A single-object answer is not mapIt, so no map statement is expected
    assertNoProblems(checkMapStatementMatchesMapIt(message));
  }
}
