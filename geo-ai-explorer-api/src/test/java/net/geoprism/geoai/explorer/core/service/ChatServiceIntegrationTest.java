package net.geoprism.geoai.explorer.core.service;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class ChatServiceIntegrationTest
{

  @Autowired
  private ChatService service;

  @Test
  public void testGetLocations() throws IOException
  {
//    History history = new History();
//    history.addMessage(new HistoryMessage(HistoryMessageType.USER, "what is the total population impacted if channel reach_25 floods?"));
//    history.addMessage(new HistoryMessage(HistoryMessageType.AI, "I found multiple channel reaches with \"25\" in their name. Could you please specify which one you're interested in? Here are some examples of the different reaches (showing first few):\n1. CEMVK_LM_09_LPM_25\n2. CEMVM_LM_26_HIK_25\n3. CEMVK_BR_01_FUL_25\n4. CELRN_TN_ND_PW2_25\n5. CELRN_TN_ND_GU1_25\n...and many more."));
//    history.addMessage(new HistoryMessage(HistoryMessageType.USER, "CEMVK_RR_03_ONE_25"));
//
//    List<LocationPage> pages = service.getLocations(history);
//
//    Assert.assertTrue(pages.size() > 0);
//
//    // Test serialization
//    ObjectMapper mapper = new ObjectMapper();
//
//    for (LocationPage page : pages) {
//      for (Location location : page.getLocations())
//      {
//  
//        try (StringWriter writer = new StringWriter())
//        {
//          mapper.writeValue(writer, location);
//  
//          String value = writer.toString();
//  
//          Assert.assertTrue(value.length() > 0);
//        }
//  
//      }
//    }

  }

}