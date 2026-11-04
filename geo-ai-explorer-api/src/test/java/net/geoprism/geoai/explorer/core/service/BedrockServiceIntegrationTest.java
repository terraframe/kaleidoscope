package net.geoprism.geoai.explorer.core.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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
import net.geoprism.geoai.explorer.core.model.History;
import net.geoprism.geoai.explorer.core.model.HistoryMessage;
import net.geoprism.geoai.explorer.core.model.HistoryMessageType;
import net.geoprism.geoai.explorer.core.model.Message;
import net.geoprism.geoai.explorer.core.model.NameResolutionResult;
import net.geoprism.geoai.explorer.core.repository.ProjectRepository;
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

    Assert.assertTrue(message.getContent().trim().length() > 0);
    Assert.assertTrue(message.getContent().contains("200"));
    Assert.assertFalse(message.getAmbiguous());
    Assert.assertTrue("Failed with message: " + message.getContent(), message.getMappable());
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

    Assert.assertTrue("Failed with message: " + message.getContent(), message.getAmbiguous());
  }

  @Test
  public void testMapIt_GetLocationsAggregation() throws InterruptedException, ExecutionException, TimeoutException
  {
    History history = new History();
    history.addMessage(new HistoryMessage(HistoryMessageType.USER, "what is the total population of a land parcel with a name of Land Parcel 23?"));
    history.addMessage(new HistoryMessage(HistoryMessageType.AI, """
        I found three land parcels named **Land Parcel 23**. Which one do you mean?

        - [structure_25 — CK1_23](https://localhost/graph#LandParcel-CK1_23)
        - [structure_25 — CMD_23](https://localhost/graph#LandParcel-CMD_23)
        - [structure_25 — LDA_23](https://localhost/graph#LandParcel-LDA_23)
            """));
    history.addMessage(new HistoryMessage(HistoryMessageType.USER, "LDA_23"));

    String sparql = service.getLocationSparql(history);

    Assert.assertTrue(sparql.contains("LDA_23"));
    Assert.assertTrue(sparql.contains("LocatedIn"));
    Assert.assertTrue(sparql.contains("?type"));
    Assert.assertTrue(sparql.contains("?code"));
    Assert.assertTrue(sparql.contains("?label"));
    Assert.assertTrue(sparql.contains("?wkt"));
  }
}
