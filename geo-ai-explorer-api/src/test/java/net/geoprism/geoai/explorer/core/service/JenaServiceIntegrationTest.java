package net.geoprism.geoai.explorer.core.service;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.geoprism.geoai.explorer.core.config.TestConfiguration;
import net.geoprism.geoai.explorer.core.model.Location;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = TestConfiguration.class)
@AutoConfigureMockMvc
public class JenaServiceIntegrationTest
{

  @Autowired
  private GraphQueryService service;

  // write test cases here
  @Test
  @Ignore
  public void test()
  {
    StringBuilder statement = new StringBuilder();
    statement.append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n");
    statement.append("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n");
    statement.append("PREFIX obj: <https://localhost:4200/lpg/rdfs#>\n");
    statement.append("PREFIX custom: <https://localhost:4200/lpg/graph_801104/0/rdfs#>\n");
    statement.append("PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n");

    statement.append("SELECT DISTINCT ?uri ?type ?code ?label ?wkt\n");
    statement.append("FROM <https://localhost:4200/lpg/graph_801104/0#>\n");
    statement.append("WHERE {\n");
    statement.append("?channel rdf:type custom:ChannelReach ; \n");
    statement.append("obj:GeoObject-code \"CEMVK_RR_03_ONE_25\" . \n");
    statement.append("?channel custom:FlowsInto* ?downstream . \n");
    statement.append("?downstream custom:ChannelHasLevee ?leveeArea . \n");
    statement.append("?leveeArea custom:HasFloodZone ?leveedArea . \n");
    statement.append("?leveedArea custom:HasFloodRisk ?uri . \n");
    statement.append("?uri rdf:type custom:School ; \n");
    statement.append("a ?type ; \n");
    statement.append("obj:GeoObject-code ?code ; \n");
    statement.append("rdfs:label ?label ; \n");
    statement.append("geo:hasGeometry ?geometry . \n");
    statement.append("?geometry geo:asWKT ?wkt .\n");
    statement.append("}\n");

    List<Location> results = service.query(statement.toString());

    Assert.assertTrue(results.size() > 0);
  }

  // write test cases here
  @Test
  @Ignore
  public void testGetAttributes()
  {
    String uri = "https://localhost:4200/lpg/graph_801104/0#CensusTract-06001407101";

    Location location = this.service.getAttributes(uri, false);

    Assert.assertNotNull(location);
    Assert.assertNull(location.getGeometry());
    Assert.assertNotNull(location.getProperties().get("type"));
    Assert.assertNotNull(location.getProperties().get("uri"));

    location = this.service.getAttributes(uri, true);

    Assert.assertNotNull(location);
    Assert.assertNotNull(location.getGeometry());
    Assert.assertNotNull(location.getProperties().get("type"));
    Assert.assertNotNull(location.getProperties().get("uri"));
  }

  @Test
  public void testSelect()
  {
    String statement = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX obj: <https://spn.geoprism.net#>

        SELECT DISTINCT ?structure ?pop2pmu65
        FROM <https://spn.geoprism.net/spn>          
        WHERE {
          ?scenario rdf:type obj:FloodScenario .
          ?scenario obj:GeoObject-code "1" .
          ?scenario obj:HasFloodRisk ?landParcel .
          ?landParcel rdf:type obj:LandParcel .
          ?structure obj:LocatedIn ?landParcel .
          ?structure obj:Structure-pop2pmu65 ?pop2pmu65 .
        }
        LIMIT 10
      """;

    List<Map<String, Object>> results = this.service.select(statement);

    Assert.assertTrue(results.size() > 0);

  }
}