package net.geoprism.geoai.explorer.core.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.model.History;
import net.geoprism.geoai.explorer.core.model.HistoryMessage;
import net.geoprism.geoai.explorer.core.model.HistoryMessageType;
import net.geoprism.geoai.explorer.core.model.Message;
import net.geoprism.geoai.explorer.core.model.NameResolutionResult;
import net.geoprism.geoai.explorer.core.service.search.BasicSearchService;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class BedrockServiceIntegrationTest
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
            .uri("lpg:Structure-2012X")
            .type("Structure")
            .label("849W84F9+M6C-4-5-3-4")
            .code("2012X")
            .build() //
    ));

    when(queryService.select(anyString())).thenReturn(List.of(Map.of("value", 431826)));
  }

  @Test
  public void testNameResolutionAndFollowUp() throws InterruptedException, ExecutionException, TimeoutException
  {
    String sessionId = UUID.randomUUID().toString();

    Message message = service.prompt(sessionId, "Give me the value of a structure with the name 849W84F9+M6C-4-5-3-4");

    System.out.println(message.getContent());

    Assert.assertTrue(message.getContent().trim().length() > 0);
    Assert.assertTrue(message.getContent().contains("431,826"));
    Assert.assertFalse(message.getContent().contains("<location><label>"));
    Assert.assertFalse(message.getContent().contains("<name>"));
    Assert.assertFalse(message.getMappable());
    Assert.assertFalse(message.getAmbiguous());
    Assert.assertEquals(sessionId, message.getSessionId());
  }

  @Test
  public void testMappable() throws InterruptedException, ExecutionException, TimeoutException
  {
    when(searchService.nameResolution(anyString())).thenReturn(List.of( //
        NameResolutionResult.builder() //
            .uri("lgs:FloodScenario-1")
            .type("FloodScenario")
            .label("No Mitigation")
            .code("1")
            .build() //
    ));

    when(queryService.select(anyString())).thenReturn(List.of(Map.of("total", 200)));

    String sessionId = UUID.randomUUID().toString();

    Message message = service.prompt(sessionId, "How many land parcels at risk with the no mitigation flood scenario?");

    System.out.println(message.getContent());

    Assert.assertTrue(message.getContent().trim().length() > 0);
    Assert.assertTrue(message.getContent().contains("200"));
    Assert.assertTrue(message.getMappable());
    Assert.assertFalse(message.getAmbiguous());
    Assert.assertEquals(sessionId, message.getSessionId());
  }

  @Test
  public void testAmbiguousName() throws InterruptedException, ExecutionException, TimeoutException
  {
    when(searchService.nameResolution(anyString())).thenReturn(List.of( //
        NameResolutionResult.builder() //
            .uri("https://localhost/graph#-CELRN_CL_ND_MEL_25")
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

  // @Test
  @Ignore
  public void testGetLocationsAggregation() throws InterruptedException, ExecutionException, TimeoutException
  {
    History history = new History();
    history.addMessage(new HistoryMessage(HistoryMessageType.USER, "what is the total population impacted if channel reach_25 floods?"));
    history.addMessage(new HistoryMessage(HistoryMessageType.AI,
        "I found multiple channel reaches that match your search. Please specify which one you're interested in:\\n\\n<name>reach_25</name>\\n<location><label>CELRN_CL_ND_MEL_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CL_ND_MEL_25</uri></location>\\n<location><label>CELRN_CR_ND_BL1_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CR_ND_BL1_25</uri></location>\\n<location><label>CELRN_CR_ND_CH1_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CR_ND_CH1_25</uri></location>\\n<location><label>CELRN_CR_ND_COR_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CR_ND_COR_25</uri></location>\\n<location><label>CELRN_CR_ND_OLD_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CR_ND_OLD_25</uri></location>"));
    history.addMessage(new HistoryMessage(HistoryMessageType.USER, "CEMVK_RR_03_ONE_25"));

    String sparql = service.getLocationSparql(history);

    Assert.assertTrue(sparql.contains("CEMVK_RR_03_ONE_25"));
    Assert.assertTrue(sparql.contains("ChannelHasLevee"));
    Assert.assertTrue(sparql.contains("HasFloodZone"));
    Assert.assertTrue(sparql.contains("TractAtRisk"));
    Assert.assertTrue(sparql.contains("hasGeometry"));
    Assert.assertTrue(sparql.contains("?type"));
    Assert.assertTrue(sparql.contains("?code"));
    Assert.assertTrue(sparql.contains("?label"));
    Assert.assertTrue(sparql.contains("?wkt"));
  }

  // @Test
  @Ignore
  public void testGetLocations() throws InterruptedException, ExecutionException, TimeoutException
  {
    History history = new History();
    history.addMessage(new HistoryMessage(HistoryMessageType.USER, "what is the total population impacted if channel reach_25 floods?"));
    history.addMessage(new HistoryMessage(HistoryMessageType.AI,
        "I found multiple channel reaches that match your search. Please specify which one you're interested in:\\n\\n<name>reach_25</name>\\n<location><label>CELRN_CL_ND_MEL_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CL_ND_MEL_25</uri></location>\\n<location><label>CELRN_CR_ND_BL1_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CR_ND_BL1_25</uri></location>\\n<location><label>CELRN_CR_ND_CH1_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CR_ND_CH1_25</uri></location>\\n<location><label>CELRN_CR_ND_COR_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CR_ND_COR_25</uri></location>\\n<location><label>CELRN_CR_ND_OLD_25</label><uri>https://localhost:4200/lpg/graph_801104/0#ChannelReach-CELRN_CR_ND_OLD_25</uri></location>"));
    history.addMessage(new HistoryMessage(HistoryMessageType.USER, "CEMVK_RR_03_ONE_25"));
    history.addMessage(new HistoryMessage(HistoryMessageType.AI, "The total population that would be impacted if channel reach CEMVK_RR_03_ONE_25 floods is 431,826 people."));
    history.addMessage(new HistoryMessage(HistoryMessageType.USER, "what school zones are impacted?"));

    String sparql = service.getLocationSparql(history);

    Assert.assertTrue(sparql.contains("CEMVK_RR_03_ONE_25"));
    Assert.assertTrue(sparql.contains("ChannelHasLevee"));
    Assert.assertTrue(sparql.contains("HasFloodZone"));
    Assert.assertTrue(sparql.contains("HasFloodRisk"));
    Assert.assertTrue(sparql.contains("HasSchoolZone"));
    Assert.assertTrue(sparql.contains("hasGeometry"));
    Assert.assertTrue(sparql.contains("?type"));
    Assert.assertTrue(sparql.contains("?code"));
    Assert.assertTrue(sparql.contains("?label"));
    Assert.assertTrue(sparql.contains("?wkt"));
  }

}
