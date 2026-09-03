package net.geoprism.geoai.explorer.core.service.prompt;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Kaleidoscope-specific chat prompt containing the schema, use cases,
 * relationships, business rules, and SPARQL examples for the current dataset.
 *
 * <p>This service intentionally preserves the original Kaleidoscope prompt
 * while the parent {@link ChatPromptService} remains data agnostic.</p>
 */
@Service
@ConditionalOnProperty(
    name = "data.usecase",
    havingValue = "kaleidoscope",
    matchIfMissing = true
)
public class KaleidoscopeChatPromptService extends ChatPromptService
{
  @Override
  protected String instructions()
  {
    String result = super.instructions();
    
    result += """
            
            The graph contains data for three primary usecases:
    1. A demo 'business data aggregation' scenario designed to showcase AI's ability to aggregate project costing data up to a program. If the user is asking about budget or costing, you know you're in this usecase. Query only <http://dime.usace.mil/data/dataset#REMIS_PROJECTS> and do not join to the lpg graph unless the user explicitly asks for mapping/connected objects. If the user asks "how much was spent on program", you MUST perform the 'budget aggregate query' (example shown at the end), taking care not to cross product.
    2. Inundated Area: a sample inundation scenario for the Robert S. Kerr Reservoir. This represents an area of predicted flood water inundation based on a flood water analysis performed by a separate expert system, represented as a polygon. Query only <%2$s>. By traversing the 'InundatedObject' edge, you can determine which objects were predicted to be 'inundated' (or flooded) based on the flood water inundation analysis.
    3. Questions related to levee areas, school zones, and real property / recreation areas. These questions will come in the form of "what is the total population impacted if channel xyz floods and overflows its levee areas", "what school zones are impacted?", etc. Query only <%2$s>. Questions about population can be answered by utilizing the 'population' attribute on CensusTract. Questions about flooded objects (i.e. hospitals or schools) can usually be answered by navigating to a LeveeArea or a LeveedArea and then navigating the relationship to find the affected objects. If you answer a population question, always include #mapit.
            """;
    
    return result;
  }

  @Override
  protected String prefixes()
  {
    String result = super.prefixes();
    
    result += """
    PREFIX apex: <http://dime.usace.mil/data/dataset#>
    PREFIX cwbi: <http://dime.usace.mil/ontologies/cwbi-concept#>
    PREFIX pm: <http://data.sec.usace.army.mil/ontologies/pm#>
    PREFIX pmcommon: <http://data.sec.usace.army.mil/common/ont/pm#>
    PREFIX sdsfie: <http://dime.usace.org/taxonomy/sdsfie/>
    PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
    PREFIX lpg: <https://localhost:4200/lpg#>
    PREFIX lpgv: <%1$s#>
    PREFIX lpgvs: <%1$s/rdfs#>
        """;
    
    return result;
  }

  @Override
  protected String graphs()
  {
    return """
    =
    Graphs
    =
    The database does NOT include any data in the default graph. When executing queries, you must always specify one or more graphs in the FROM clause, or you may specify a graph wildcard to query all graphs.
    
    There are two separate graphs in this database:
    - <%2$s>
    - <http://dime.usace.mil/data/dataset#REMIS_PROJECTS>
    
    Never put lpgv and cwbi triples into the same GRAPH block.
    
    The first graph contains a vast wealth of location data.
    The REMIS_PROJECTS graph is mostly used for joining data against the first, specifically for usage in a 'project' or 'remis project' context. This graph contains program, REMIS project, budget, and costing data.
    
    The lpg schema uses the concept of a GeoObject. A GeoObject is a spatial concept and can be considered a formalized extension of a traditional GIS feature. A GeoObjectType contains the metadata that defines a concrete GeoObject.
        """;
  }

  @Override
  protected String types()
  {
    return """
    =
    Types
    =
    A CSV list of (graph, type) pairs. This is the full list of rdf:type within the database.
    
    lpgv,lpgvs:CensusTract
    lpgv,lpgvs:Hospital
    lpgv,lpgvs:Dam
    lpgv,lpgvs:Project
    lpgv,lpgvs:LeveeArea
    lpgv,lpgvs:RealProperty
    lpgv,lpgvs:WaterTransportation
    lpgv,lpgvs:ChannelArea
    lpgv,lpgvs:ChannelReach
    lpgv,lpgvs:LandTransportation
    lpgv,lpgvs:RecreationArea
    lpgv,lpgvs:School
    lpgv,lpgvs:State
    lpgv,lpgvs:LeveedArea
    lpgv,lpgvs:SchoolZone
    lpgv,lpgvs:County
    lpgv,lpgvs:UsaceRecreationArea
    lpgv,lpgvs:InundationArea
    apex:REMIS_PROJECTS,cwbi:Program
        """;
  }

  @Override
  protected String edges()
  {
    return """
    =
    Edges
    =
    
    A list of relationships between types. The relationship format is described as (SourceType)->[EdgeType]->(TargetType) and is directional from left to right. If a relationship is bi-directional it will be listed twice, one in each direction.
    
    
    (cwbi:Remis_Project)->[cwbi:Program]->(cwbi:Program)
    (lpgvs:CensusTract)->[lpgvs:TractAtRisk]->(lpgvs:LeveedArea)
    (lpgvs:ChannelReach)->[lpgvs:ChannelHasLevee]->(lpgvs:LeveeArea) 
    (lpgvs:ChannelReach)->[lpgvs:FlowsInto]->(lpgvs:ChannelReach)
    (lpgvs:LeveeArea)->[lpgvs:HasFloodZone]->(lpgvs:LeveedArea)
    (lpgvs:LeveedArea)->[lpgvs:HasFloodRisk]->(lpgvs:Hospital)
    (lpgvs:LeveedArea)->[lpgvs:HasFloodRisk]->(lpgvs:RealProperty)
    (lpgvs:LeveedArea)->[lpgvs:HasFloodRisk]->(lpgvs:School)
    (lpgvs:SchoolZone)->[lpgvs:HasSchoolZone]->(lpgvs:School)
    (lpgvs:InundationArea)->[lpgvs:InundatedObject]->(lpgvs:School)
    (lpgvs:InundationArea)->[lpgvs:InundatedObject]->(lpgvs:Hospital)
    (lpgvs:InundationArea)->[lpgvs:InundatedObject]->(lpgvs:Project)
    (lpgvs:InundationArea)->[lpgvs:InundatedObject]->(lpgvs:RealProperty)
    (lpgvs:InundationArea)->[lpgvs:InundatedObject]->(lpgvs:UsaceRecreationArea)
    (lpgvs:InundationArea)->[lpgvs:InundatedObject]->(lpgvs:RecreationArea)
    (lpgvs:InundationArea)->[lpgvs:InundatedObject]->(lpgvs:Dam)
    (lpgvs:InundationArea)->[lpgvs:InundatedObject]->(lpgvs:LeveeArea)
    (lpgvs:InundationArea)->[lpgvs:InundatedObject]->(lpgvs:LeveedArea)
    
    The lpgvs:ConnectedTo can be used bi-directionally with any of the following types as a source or target:
    lpgvs:RecreationArea, lpgvs:WaterBody, lpgvs:UsaceRecreationArea, lpgvs:Project, lpgvs:LandTransportation, lpgvs:ChannelArea, lpgvs:ChannelReach, lpgvs:Waterway, lpgvs:LeveeArea, lpgvs:WaterTransportation.
    
    Examples:
    
    Valid:
    ?leveedArea lpgvs:HasFloodRisk ?school .
    
    INVALID:
    ?school lpgvs:HasFloodRisk ?leveedArea .
    
    Why?
    Because you did not respect the order of the relationship!
        """;
  }

  @Override
  protected String attributes()
  {
    return """
    =
    Attributes
    =
    
    There are many ‘data’ attributes which exist on these types for which various information can be fetched. These data attributes, for example, may define a display label, a code, or even a ‘population’ which might be required to service a particular user query.
    
    
    rdfs:label - A string literal, defines the label of the GeoObject
    lpgs:GeoObjectType-code - A string literal, defines the code of the GeoObject. Code is the uniqueness constraint for this dataset.
    lpgs:GeoObjectType-uid - A generated, unique UUID for the object. Should not be shown to the end user as it does not contain much significance. Use code instead.
    
    
    The ‘apex:’ datasets use the following attributes:
    rdfs:label - A string literal, defines the label of the object
    skos:altLabel - A string literal, often contains the code of the object (although not guaranteed)
    dct:description - A string literal, sometimes contains a description of the object
    
    
    # Code can either be GeoObject-code or altLabel, depending on which graph it comes from
    OPTIONAL { ?s lpgs:GeoObject-code ?geoCode . }
    OPTIONAL { ?s skos:altLabel ?altCode . }
    BIND(COALESCE(?geoCode, ?altCode) AS ?code)
    
    
    The following types contain domain specific attribution, where the type is listed first and an iri for the predicate which links to the literal is listed second, and then a description of the attribute is listed third.
    
    
    lpgvs:RealProperty, lpgvs:RealProperty-realPropertyType, String literal. Specifies the type of the property
    lpgvs:RealProperty, lpgvs:RealProperty-realPropertyUse, String literal. Specifies the usage of the property
    lpgvs:School, lpgvs:School-population, Number. Population of the school.
    lpgvs:CensusTract, lpgvs:CensusTract-population, Number. Population of the census tract.
    
    When answering questions about population, you need to use CensusTract-population unless the user explicitly mentions number of students. Do not query for hospitals and real properties on flood zones to answer this question as it will not be accurate.
        """;
  }
  
  @Override
  protected String schemaAddendum()
  {
    return """
        =
        Joining Data
        =
        The data of type lpgvs:Project is conceptually the same as cwbi:Remis_Project, both objects have the same code and are conceptually the same:
        ?proj a lpgvs:Project .
        ?proj lpgs:GeoObject-code "30000667" .
        ?remisproj a cwbi:Remis_Project .
        ?remisproj skos:altLabel "30000667" .
        
        You can therefore start with a cwbi:Program, navigate the cwbi:Program edge to get cwbi:Remis_Project, join that (by code) against lpgvs:Project, and then navigate from there across the lpgvs:ConnectedTo edge to find all sorts of levees and reaches and recreation areas.
        
        All project and program data can be mapped so please return a #mapit it when giving project or program information.

        =
        Flood Inundation
        =
        If the user asks 'Which objects are inundated', you ONLY need to consider these objects:
        InundationArea -> InundatedObject -> ?object
        
        If the user asks about what Inundation scenarios are available, query InundationArea and return the results (along with #mapit).
              """;
  }

  @Override
  protected List<String> buildExamples()
  {
    List<String> examples = new ArrayList<String>();
    
    examples.add(aggregateFunctions());
    examples.add(budgetLineItemExample());
    examples.add(budgetAggregateExample());
    examples.add(crossGraphJoinExample());
    
    return examples;
  }
  
  protected String aggregateFunctions()
  {
    return """
        =
        Aggregation functions
        =
        
        Every aggregation expression must be wrapped in parentheses and assigned to a variable.

        Incorrect:
        SELECT SUM(?population) AS ?totalPopulation
        
        Correct:
        SELECT (SUM(?population) AS ?totalPopulation)
        
        When object results and aggregates are both required, calculate the aggregate in a subquery.
        
        Example:
        
        PREFIX apex: <http://dime.usace.mil/data/dataset#>
        PREFIX cwbi: <http://dime.usace.mil/ontologies/cwbi-concept#>
        PREFIX pm: <http://data.sec.usace.army.mil/ontologies/pm#>
        PREFIX pmcommon: <http://data.sec.usace.army.mil/common/ont/pm#>
        PREFIX sdsfie: <http://dime.usace.org/taxonomy/sdsfie/>
        PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
        PREFIX lpg: <https://localhost:4200/lpg#>
        PREFIX lpgv: <https://localhost:4200/lpg/graph_801104/0#>
        PREFIX lpgvs: <https://localhost:4200/lpg/graph_801104/0/rdfs#>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX dct: <http://purl.org/dc/terms/>
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        
        SELECT DISTINCT
        ?uri
        ?type
        ?code
        ?label
        ?wkt
        ?population
        ?totalPopulation
        WHERE {
        GRAPH <https://localhost:4200/lpg/graph_801104/0#> {
        ?parent rdf:type lpgvs:ChannelReach ;
        lpgs:GeoObject-code "CEMVK_RR_03_ONE_27" .
        
        
        ?parent lpgvs:FlowsInto* ?channel .
        ?channel lpgvs:ChannelHasLevee ?leveeArea .
        ?leveeArea lpgvs:HasFloodZone ?leveedArea .
        
        ?uri rdf:type lpgvs:CensusTract ;
             lpgvs:TractAtRisk ?leveedArea ;
             lpgs:GeoObject-code ?code ;
             rdfs:label ?label ;
             lpgvs:CensusTract-population ?population .
        
        BIND("CensusTract" AS ?type)
        
        OPTIONAL {
          ?uri geo:hasGeometry ?geometry .
          ?geometry geo:asWKT ?wkt .
        }
        
        
        }
        
        {
        SELECT (SUM(?tractPopulation) AS ?totalPopulation)
        WHERE {
        GRAPH <https://localhost:4200/lpg/graph_801104/0#> {
        ?aggregateParent rdf:type lpgvs:ChannelReach ;
        lpgs:GeoObject-code "CEMVK_RR_03_ONE_27" .
        
        
            ?aggregateParent lpgvs:FlowsInto* ?aggregateChannel .
            ?aggregateChannel lpgvs:ChannelHasLevee ?aggregateLeveeArea .
            ?aggregateLeveeArea lpgvs:HasFloodZone ?aggregateLeveedArea .
        
            ?aggregateTract rdf:type lpgvs:CensusTract ;
                            lpgvs:TractAtRisk ?aggregateLeveedArea ;
                            lpgvs:CensusTract-population ?tractPopulation .
          }
        }
        
        
        }
        }
        ORDER BY ASC(?label)
        LIMIT 100
            """;
  }

  protected String budgetLineItemExample()
  {
    return """
    =
    Usecase 1: 'budget line item'
    This is useful for listing costing information for individual projects. This query should NOT be used for aggregate program costing data.
    
    Q: "How much was spent on projects associated with a program with code 000510?" 
    A: Costing data for all projects of a specific program can be queried with the following
    =
    
    PREFIX cwbi: <http://dime.usace.mil/ontologies/cwbi-concept#>
    PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
    PREFIX lpg: <https://localhost:4200/lpg#>
    PREFIX lpgv: <%1$s#>
    PREFIX lpgvs: <%1$s/rdfs#>
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
    PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
    
    SELECT DISTINCT ?program ?rem_proj ?cost
    WHERE {
      BIND("000510" AS ?code)
    
      GRAPH <http://dime.usace.mil/data/dataset#REMIS_PROJECTS> {
        ?program a cwbi:Program ;
                 skos:altLabel ?code .
        ?rem_proj a cwbi:Remis_Project ;
                  cwbi:Program ?program ;
                  skos:altLabel ?lbl ;
                  cwbi:ProjectCost ?cost.
      }
    }
    LIMIT 100
        """;
  }

  protected String budgetAggregateExample()
  {
    return """
    =
    Usecase 1: 'budget aggregate query'
    
    Q: "How much was spent on a program with code '000510'?"
    Q: "What is the budget for a program with code '000510'?"
    Q: "How much budget is remaining on program '000510'?"
    A: Budgetary information is stored at the program level and costing (actuals) data is stored at the project level. Costing data can be aggregated up to a program and then compared with the program budget data to gather remaining budget.
    =
    
    PREFIX cwbi: <http://dime.usace.mil/ontologies/cwbi-concept#>
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
    PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    
    SELECT
      ?program
      ("000510" AS ?programCode)
      ?programLabel
      ?budget
      ?projectCount
      ?totalCost
      ?avgCost
      ?minCost
      ?maxCost
      ( xsd:decimal(COALESCE(?budget, 0)) - ?totalCost AS ?remainingBudget )
    WHERE {
      # 1) Aggregate projects & costs (no labels here)
      {
        SELECT
          ?program
          ?budget
          (COUNT(DISTINCT ?rem_proj) AS ?projectCount)
          (SUM(xsd:decimal(?cost))   AS ?totalCost)
          (AVG(xsd:decimal(?cost))   AS ?avgCost)
          (MIN(xsd:decimal(?cost))   AS ?minCost)
          (MAX(xsd:decimal(?cost))   AS ?maxCost)
        WHERE {
          GRAPH <http://dime.usace.mil/data/dataset#REMIS_PROJECTS> {
            ?rem_proj a cwbi:Remis_Project ;
                      cwbi:Program ?program ;
                      cwbi:ProjectCost ?cost .
            ?program  skos:altLabel "000510" ;
                      cwbi:ProgramBudget ?budget .
          }
        }
        GROUP BY ?program ?budget
      }
    
      # 2) IMPORTANT: Notice how this label query happens in a separate query from the aggregate query above. If we were to include this inside the query above, it would screw up the aggregation results by introducing cross products.
      OPTIONAL {
        SELECT ?program (SAMPLE(?lbl) AS ?programLabel)
        WHERE {
          GRAPH <http://dime.usace.mil/data/dataset#REMIS_PROJECTS> {
            ?program rdfs:label ?lbl .
          }
        }
        GROUP BY ?program
      }
    }
    ORDER BY DESC(?totalCost)
        """;
  }

  protected String crossGraphJoinExample()
  {
    return """
    =
    This example showcases a join between the two graphs, by utilizing the shared 'code' data.
    
    Q: Show me all objects which are reachable from a program with code 000510
    =
    
    PREFIX cwbi: <http://dime.usace.mil/ontologies/cwbi-concept#>
    PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
    PREFIX lpg: <https://localhost:4200/lpg#>
    PREFIX lpgv: <%1$s#>
    PREFIX lpgvs: <%1$s/rdfs#>
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX geo: <http://www.opengis.net/ont/geosparql#>
    
    SELECT ?proj ?projCode ?label ?wkt
    WHERE {
      BIND("000510" AS ?programCode)
    
      GRAPH <http://dime.usace.mil/data/dataset#REMIS_PROJECTS> {
        ?program a cwbi:Program ;
                 skos:altLabel ?programCode .
        ?rem_proj a cwbi:Remis_Project ;
                 cwbi:Program ?program ;
                 skos:altLabel ?projCode .
      }
    
      GRAPH <%2$s> {
        ?proj a lpgvs:Project ;
            lpgs:GeoObject-code ?projCode ;
            lpgvs:Project-programCode ?code ;
            rdfs:label ?label .
      }
    }
    LIMIT 100
        """;
  }
}