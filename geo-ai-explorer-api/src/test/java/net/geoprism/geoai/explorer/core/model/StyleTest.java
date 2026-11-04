package net.geoprism.geoai.explorer.core.model;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class StyleTest
{

  @Test
  public void testToText()
  {
    Map<String, Style> map = new HashMap<>();

    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#Hospital", new Style("#F2799D", 0));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#Dam", new Style("#D5F279", 0));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#Project", new Style("#C0F279", 6));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#Watershed", new Style("#79F2C9", 4));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#LeveeArea", new Style("#D1D1D1", 4));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#RealProperty", new Style("#79F294", 0));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#Reservoir", new Style("#CAEEFB", 5));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#ChannelArea", new Style("#156082", 4));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#ChannelReach", new Style("#79DAF2", 4));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#RecreationArea", new Style("#F2E779", 3));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#School", new Style("#F2A579", 0));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#ChannelLine", new Style("#79F2A0", 1));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#LeveedArea", new Style("#C379F2", 4));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#River", new Style("#7999F2", 2));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#SchoolZone", new Style("#FBE3D6", 6));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#Levee", new Style("#F279E0", 0));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#WaterLock", new Style("#79F2E2", 0));
    map.put("https://localhost:4200/lpg/graph_801104/0/rdfs#UsaceRecreationArea", new Style("#F2BE79", 3));
    map.put("http://dime.usace.mil/ontologies/cwbi-concept#Program", new Style("#FF5733", 0));

    ObjectMapper mapper = new ObjectMapper();

    try
    {
      System.out.println(mapper.writeValueAsString(map));
    }
    catch (JacksonException e)
    {
      throw new RuntimeException(e);
    }
  }

}