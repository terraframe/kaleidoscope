package net.geoprism.geoai.explorer.core.service.prompt;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "data.usecase",
    havingValue = "kaleidoscope",
    matchIfMissing = true
)
public class KaleidoscopeMapItPromptService extends MapItPromptService
{
  @Override
  protected String instructions()
  {
    return super.instructions() + """
            * Never place LPG triples and REMIS/CWBI triples in the same GRAPH block.
            
            If the chat history is only discussing budgetary information about a program, return the projects associated with that program, their geometries, their project-level costs when available, and any requested program-level aggregate values.
            
            When generating SPARQL about a program, do not reuse unrelated information from an earlier channel-reach discussion.
            
            When generating SPARQL about an inundation scenario, ignore unrelated program or channel-reach context and focus on the specified InundationArea and its InundatedObject relationships.
            """;
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
    
    Never place lpgv/lpgvs triples and cwbi/REMIS triples in the same GRAPH block.

    The LPG graph contains location and geometry data.
    
    The lpg schema uses the concept of a GeoObject. A GeoObject is a spatial concept and can be considered a formalized extension of a traditional GIS feature. A GeoObjectType contains the metadata that defines a concrete GeoObject.
    
    Questions about population should be answered using the population attribute on CensusTract unless the user explicitly asks for student population.
    
    Questions about students should use the population attribute on School.
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
        An InundationArea represents the result of a flood-water inundation analysis expert system and is represented as a polygon.
        
        Traverse the InundatedObject relationship to determine which objects were predicted to be inundated.
        
        Questions about flooded objects, such as hospitals or schools, can usually be answered by navigating from a LeveeArea to a LeveedArea and then to the affected objects.
        
        If the user asks 'Which objects are inundated', you ONLY need to consider these objects:
        InundationArea -> InundatedObject -> ?object
        
        If the user asks about what Inundation scenarios are available, query InundationArea and return the results (along with #mapit).
              """;
  }
  
  @Override
  protected List<String> buildExamples()
  {
    List<String> examples = super.buildExamples();
    examples.add(attributeInclusionExample());
    examples.add(selfReferencingExample());
    
    return examples;
  }
  
  private String attributeInclusionExample()
  {
    return """
            A final reminder: the query MUST also return every available type-specific attribute for the selected node type.
      
            Examples:
            
            * When returning CensusTract objects, also select ?population.
            * When returning School objects, also select ?population.
            * When returning RealProperty objects, also select ?realPropertyType and ?realPropertyUse.
            * When returning projects associated with financial data, include applicable attributes such as ?cost.
            * When returning objects for an aggregate question, include the aggregate result as an additional variable while still returning the required object variables.
            """;
  }
  
  private String selfReferencingExample()
  {
    return """
            =
            Self-referencing relationships
            =

            A self-referencing relationship should use * when transitive traversal is required.
            
            Incorrect:
            
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
            WHERE {
            GRAPH <https://localhost:4200/lpg/graph_801104/0#> {
            ?parent rdf:type lpgvs:ChannelReach ;
            lpgs:GeoObject-code "CEMVK_RR_03_ONE_27" .
            
            
            ?parent lpgvs:FlowsInto ?channel .
            ?channel lpgvs:ChannelHasLevee ?uri .
            
            ?uri rdf:type lpgvs:LeveeArea ;
                 lpgs:GeoObject-code ?code ;
                 rdfs:label ?label .
            
            BIND("LeveeArea" AS ?type)
            
            OPTIONAL {
              ?uri geo:hasGeometry ?geometry .
              ?geometry geo:asWKT ?wkt .
            }
            
            
            }
            }
            ORDER BY ASC(?label)
            LIMIT 100
            
            Correct:
            
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
            WHERE {
            GRAPH <https://localhost:4200/lpg/graph_801104/0#> {
            ?parent rdf:type lpgvs:ChannelReach ;
            lpgs:GeoObject-code "CEMVK_RR_03_ONE_27" .
            
            
            ?parent lpgvs:FlowsInto* ?channel .
            ?channel lpgvs:ChannelHasLevee ?uri .
            
            ?uri rdf:type lpgvs:LeveeArea ;
                 lpgs:GeoObject-code ?code ;
                 rdfs:label ?label .
            
            BIND("LeveeArea" AS ?type)
            
            OPTIONAL {
              ?uri geo:hasGeometry ?geometry .
              ?geometry geo:asWKT ?wkt .
            }
            
            
            }
            }
            ORDER BY ASC(?label)
            LIMIT 100
            """;
  }
}
