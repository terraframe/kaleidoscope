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
        geometries(), //
        mapStatement(), //
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
    examples.add(mapStatementExample());

    return examples;
  }

  protected String mapStatementExample()
  {
    return """
        =
        Map statement example
        =

        Q: How many land parcels are at risk under the No Mitigation scenario?

        Query you execute with the query tool. It only needs to count, so it does not fetch geometries:

        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX obj: <https://spn.geoprism.net#>

        SELECT (COUNT(DISTINCT ?parcel) AS ?parcelCount)
        FROM <https://spn.geoprism.net/spn>
        WHERE {
          ?scenario rdf:type obj:FloodScenario .
          ?scenario obj:GeoObject-code "1" .
          ?scenario obj:HasFloodRisk ?parcel .
          ?parcel rdf:type obj:LandParcel .
        }

        mapStatement. It returns the land parcels that were counted, with their geometries and attributes, so the user can audit the count on a map:

        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>
        PREFIX obj: <https://spn.geoprism.net#>

        SELECT DISTINCT ?uri ?type ?code ?label ?wkt ?landUse ?lotAcres ?mailAddress
        FROM <https://spn.geoprism.net/spn>
        WHERE {
          ?scenario rdf:type obj:FloodScenario .
          ?scenario obj:GeoObject-code "1" .
          ?scenario obj:HasFloodRisk ?uri .
          ?uri rdf:type obj:LandParcel .
          ?uri obj:GeoObject-code ?code .
          ?uri rdfs:label ?label .
          BIND("LandParcel" AS ?type)
          OPTIONAL { ?uri obj:LandParcel-landUse ?landUse . }
          OPTIONAL { ?uri obj:LandParcel-lotAcres ?lotAcres . }
          OPTIONAL { ?uri obj:LandParcel-mailAddress ?mailAddress . }
          OPTIONAL {
            ?uri geo:hasGeometry ?geometry .
            ?geometry geo:asWKT ?wkt .
          }
        }

        Likewise, for "How many people are in the project area with a code of "1"?" the mapStatement returns the obj:Structure objects whose obj:Structure-pop2pmu65 values were summed, selecting that value as ?population.
        """;
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
}
