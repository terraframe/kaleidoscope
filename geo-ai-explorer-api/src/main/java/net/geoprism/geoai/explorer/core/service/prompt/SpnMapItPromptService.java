package net.geoprism.geoai.explorer.core.service.prompt;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "data.usecase", havingValue = "spn", matchIfMissing = true)
public class SpnMapItPromptService extends MapItPromptService
{
  @Autowired
  private SpnMetadataService metadata;

  @Override
  protected String types()
  {
    return this.metadata.types();
  }

  @Override
  protected String edges()
  {
    return this.metadata.edges();
  }

  @Override
  protected String attributes()
  {
    return this.metadata.attributes();
  }

  @Override
  protected String schemaAddendum()
  {
    return """
        =
        Flood Scenario
        =
        There are only two flood scenarios in the database:

        A flood scenario with the label 'No Mitigation Scenario' and a code of '1'.
        A flood scenario with the label 'Combo Plan Scenario' and a code of '2'

        If needed base on the chat history use these code when generating the SPARQL.

        =
        Flood Inundation
        =
        If the user asks 'Which objects are at flood risk', you should start with these relationships:

        (obj:FloodScenario) -> [obj:HasFloodRisk] -> ...

        Answering questions about flooded structures, for example, might require traversing multiple edges, i.e.
        FloodScenario -> HasFloodRisk -> LandParcel
        Structure -> LocatedIn -> LandParcel

        If the user asks about what Inundation scenarios are available, query obj:FloodScenario and return the results.
              """;
  }

  @Override
  protected List<String> buildExamples()
  {
    List<String> examples = super.buildExamples();

    examples.add(populationUnder65());
    examples.add(noMitigation());
    examples.add(listFloodScenarios());

    return examples;
  }

  protected String populationUnder65()
  {
    return """
        =
        Population under 65
        =

        User:
        {"messages":[{"type":"USER","content":"What is the total population under 65 in the project area with a code of '1'?"}],"limit":100,"offset":0}

        Agent:
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX obj: <https://spn.geoprism.net#>
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>

        SELECT DISTINCT ?uri ?type ?code ?label ?wkt ?population
        FROM <https://spn.geoprism.net/spn>
        WHERE {
          ?projectArea rdf:type obj:ProjectArea .
          ?projectArea obj:GeoObject-code "1" .
          ?uri obj:LocatedIn ?projectArea ;
               rdf:type ?type ;
               obj:GeoObject-code ?code ;
               rdfs:label ?label ;
               geo:hasGeometry ?geometry ;
               obj:Structure-pop2pmu65 ?population .


          ?geometry geo:asWKT ?wkt .
        }
        LIMIT 100
        """;
  }

  protected String noMitigation()
  {
    return """
        =
        No mitigation flood risk
        =

        User:
        {"messages":[{"type":"USER","content":"what is the total population at flood risk for the no mitigation flood scenario?"}],"limit":100,"offset":0}

        Agent:
        PREFIX obj: <https://spn.geoprism.net#>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>
        PREFIX sf: <http://www.opengis.net/ont/sf#>

        SELECT DISTINCT ?uri ?type ?code ?label ?wkt
        FROM <https://spn.geoprism.net/spn>
        WHERE {
          ?scenario rdf:type obj:FloodScenario .
          ?scenario obj:GeoObject-code "1" .
          ?scenario obj:HasFloodRisk ?parcel .
          ?uri obj:LocatedIn ?parcel .
          ?uri rdf:type ?type .
          ?uri obj:GeoObject-code ?code .
          ?uri rdfs:label ?label .
          ?uri geo:hasGeometry ?geometry .
          ?geometry geo:asWKT ?wkt .

          FILTER(?type IN (obj:Structure))
        }
        LIMIT 100
        """;
  }

  protected String listFloodScenarios()
  {
    return """
        =
        List flood scenarios
        =

        User:
        {"messages":[{"type":"USER","content":"What flood scenarios are available?"}],"limit":100,"offset":0}

        Agent:
        PREFIX obj: <https://spn.geoprism.net#>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>
        PREFIX sf: <http://www.opengis.net/ont/sf#>

        SELECT DISTINCT ?uri ?type ?code ?label ?wkt
        FROM <https://spn.geoprism.net/spn>
        WHERE {
          ?uri rdf:type obj:FloodScenario .
          ?uri rdf:type ?type .
          ?uri obj:GeoObject-code ?code .
          ?uri rdfs:label ?label .
          ?uri geo:hasGeometry ?geometry .
          ?geometry geo:asWKT ?wkt .
        }
        LIMIT 100
        """;
  }
}
