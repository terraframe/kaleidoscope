package net.geoprism.geoai.explorer.core.service.prompt;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "data.usecase", havingValue = "spn", matchIfMissing = true)
public class SpnChatPromptService extends ChatPromptService
{
  @Autowired
  private SpnMetadataService metadata;

  @Override
  protected String build()
  {
    return joinComponents( //
        instructions(), //
        schema(), //
        prefixes(), //
        graphs(), //
        types(), //
        edges(), //
        attributes(), //
        dataSource(), //
        schemaAddendum(), //
        sparqlExamples());
  }

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

        <https://spn.geoprism.net#FloodScenario-1> label'No Mitigation Scenario', code'1'
        <https://spn.geoprism.net#FloodScenario-2> label'Combo Plan Scenario', 'code'2'

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

    examples.add(peopleInProjectArea());
    examples.add(mapItExamples());

    return examples;
  }

  protected String peopleInProjectArea()
  {
    return """
        =
        How many people are in the project area with a code of "1"?
        =

        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX obj: <https://spn.geoprism.net#>

        SELECT (SUM(?pop2pmu65) as ?totalPopulation)
        FROM <https://spn.geoprism.net/spn>
        WHERE {
          SELECT DISTINCT ?structure ?pop2pmu65
          WHERE {
            ?projectArea rdf:type obj:ProjectArea .
            ?projectArea obj:GeoObject-code "1" .
            ?structure obj:LocatedIn ?projectArea .
            ?structure obj:Structure-pop2pmu65 ?pop2pmu65 .
          }
        }
        """;
  }

  protected String mapItExamples()
  {
    return """
        =
        The following is a list of examples intended to showcase how and when to use set the mapIt parameter as true
        =

        Q: What is the total population in the flood scenario with a code of '1'?
        A: The total population in the project area with a code of '1' is 200.

        Q: What is the most common land use for parcels in the project area with a code of 'Example Location'?
        A: The most common land use for parcels in the project area with a code of 'Example Location' is Single Family.

        - Do not include the #mapIt tag if the question is comparing multiple scenarios.  For example, the following should not include set mapIt as true:
        Q: What is total value of structures at risk with the no mitigation scenario versus the combo plan mitigation scenario?
        A: The total value of structures at risk with the no mitigation scenario is $1,000 versus the combo plan mitigation scenario which is $50.
        """;
  }
}
