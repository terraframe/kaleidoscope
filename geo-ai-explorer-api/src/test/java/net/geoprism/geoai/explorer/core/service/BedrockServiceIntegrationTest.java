package net.geoprism.geoai.explorer.core.service;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.model.History;
import net.geoprism.geoai.explorer.core.model.HistoryMessage;
import net.geoprism.geoai.explorer.core.model.HistoryMessageType;
import net.geoprism.geoai.explorer.core.model.Message;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class BedrockServiceIntegrationTest
{

  @Autowired
  private BedrockService service;

//  @Test
//  public void testPrompt() throws InterruptedException, ExecutionException, TimeoutException
//  {
//     String sessionId = UUID.randomUUID().toString();
//    
//     Message message = service.prompt(sessionId, "what is the total daytime
//     population impacted with the no combo plan flood scenario?");
//    
//     Assert.assertTrue(message.getContent().trim().length() > 0);
//     Assert.assertTrue(message.getContent(), message.getContent().contains("I
//     found multiple channel"));
//     Assert.assertTrue(message.getContent().contains("<location><label>"));
//     Assert.assertNotNull(message.getLocation());
//     Assert.assertFalse(message.getMappable());
//     Assert.assertTrue(message.getAmbiguous());
//     Assert.assertEquals(sessionId, message.getSessionId());
//     Assert.assertTrue(message.getLocation(),
//     message.getLocation().contains("reach_25"));
//    
//     message = service.prompt(sessionId, "CEMVK_RR_03_ONE_25");
//    
//     Assert.assertTrue(message.getContent().trim().length() > 0);
//     Assert.assertTrue(message.getContent(),
//     message.getContent().contains("431,826"));
//     Assert.assertFalse(message.getContent(),
//     message.getContent().contains("<location><label>"));
//     Assert.assertTrue(message.getMappable());
//     Assert.assertEquals(sessionId, message.getSessionId());
//     Assert.assertFalse(message.getAmbiguous());
//    
//     message = service.prompt(sessionId, "what school zones are impacted?");
//    
//     Assert.assertTrue(message.getContent().trim().length() > 0);
//     Assert.assertTrue(message.getContent(),
//     message.getContent().toUpperCase().contains("SCHOOL DISTRICT"));
//     Assert.assertTrue(message.getContent(),
//     message.getContent().contains("<location><label>"));
//     Assert.assertTrue(message.getMappable());
//     Assert.assertEquals(sessionId, message.getSessionId());
//     Assert.assertFalse(message.getAmbiguous());
//  }
//
//  @Test
//  public void testMessage() throws InterruptedException, ExecutionException, TimeoutException
//  {
//    String sessionId = UUID.randomUUID().toString();
//
//    Message message = service.prompt(sessionId, "what is the total daytime population broken down by age bracket impacted using the no combo plan flood scenario");
//
//    System.out.println(message.getContent());
//
//    Assert.assertTrue(message.getContent().trim().length() > 0);
//    Assert.assertTrue(message.getContent().contains("431,826"));
//    Assert.assertFalse(message.getContent().contains("<location><label>"));
//    Assert.assertFalse(message.getContent().contains("<name>"));
//    Assert.assertTrue(message.getMappable());
//    Assert.assertFalse(message.getAmbiguous());
//    Assert.assertEquals(sessionId, message.getSessionId());
//  }

  @Test
  public void testPromptWithCode() throws InterruptedException, ExecutionException, TimeoutException
  {
    String sessionId = UUID.randomUUID().toString();
    
    Message message = service.prompt(sessionId, "Give me the value of a structure with the name 849W84F9+M6C-4-5-3-4");
    
    System.out.println(message.getContent());
    
    Assert.assertTrue(message.getContent().trim().length() > 0);
    Assert.assertTrue(message.getContent().contains("431,826"));
    Assert.assertFalse(message.getContent().contains("<location><label>"));
    Assert.assertFalse(message.getContent().contains("<name>"));
    Assert.assertTrue(message.getMappable());
    Assert.assertFalse(message.getAmbiguous());
    Assert.assertEquals(sessionId, message.getSessionId());
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
