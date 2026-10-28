package net.geoprism.geoai.explorer.core.service.prompt;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "data.usecase",
    havingValue = "spn",
    matchIfMissing = true
)
public class SpnChatPromptService extends ChatPromptService
{
  @Override
  protected String build()
  {
    return joinComponents(
        instructions(),
        schema(),
        prefixes(),
        graphs(),
        types(),
        edges(),
        attributes(),
        dataSource(),
        schemaAddendum(),
        sparqlExamples());
  }
  
  @Override
  protected String types()
  {
    return """
    =
    Types
    =
    A CSV list of (type, description) pairs. This is the full list of rdf:type within the database.

    obj:Creek, Contains creek data
    obj:LandParcel, Contains land parcel data
    obj:FloodScenario, Contains flood scenario data with different mitigation plans.
    obj:Lake, Contains lake data
    obj:Road, Contains road data
    obj:ComboPlanBridge, Contains mitigation data for bridges
    obj:ComboPlanFloodwall, Contains mitigation data for flood walls
    obj:ComboPlanSlopeRepair, Contains mitigation data for slope repair
    obj:ComboPlanChannelFtprnt, Contains mitigation data for channel foot prints
    obj:ComboPlanPropGravel, Contains mitigation data for gravel
    obj:ComboPlanPropRiprap, Contains mitigation data for riprap
    obj:ProjectReach, List of channel reaches in the project 
    obj:ProjectArea, List of different project areas
    obj:Structure, List of structures
        """;
  }
  
  @Override
  protected String edges()
  {
    return """
    =
    Edges
    =
    
    The following is the full list of edges between the types in the system. If an edge is not explicitly listed here between two types, you must assume the data does not exist and the path cannot be used to resolve a query.
    
    The relationship format is described as (SourceType)->[EdgeType]->(TargetType) and is directional from left to right. If a relationship is bi-directional it will be listed twice, one in each direction.

    (obj:FloodScenario)->[obj:HasFloodRisk]->(obj:Road)
    (obj:FloodScenario)->[obj:HasFloodRisk]->(obj:LandParcel)
    
    (obj:FloodScenario)->[obj:HasMitigation]->(obj:ComboPlanBridge)
    (obj:FloodScenario)->[obj:HasMitigation]->(obj:ComboPlanFloodwall)
    (obj:FloodScenario)->[obj:HasMitigation]->(obj:ComboPlanSlopeRepair)
    (obj:FloodScenario)->[obj:HasMitigation]->(obj:ComboPlanChannelFtprnt)
    (obj:FloodScenario)->[obj:HasMitigation]->(obj:ComboPlanPropGravel)
    (obj:FloodScenario)->[obj:HasMitigation]->(obj:ComboPlanPropRiprap)
    
    (obj:ComboPlanBridge)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:ComboPlanFloodwall)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:ComboPlanSlopeRepair)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:ComboPlanChannelFtprnt)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:ComboPlanPropGravel)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:ComboPlanPropRiprap)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:ProjectReach)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:Road)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:Lake)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:Structure)->[obj:LocatedIn]->(obj:ProjectArea)
    (obj:Structure)->[obj:LocatedIn]->(obj:LandParcel)
    
    The obj:FlowsInto edge can be used bi-directionally with any of the following types as a source or target:
    obj:Creek, obj:Lake, obj:ProjectReach.
    
    
    Examples:
    
    Valid:
    ?scenario obj:HasFloodRisk ?landParcel .
    
    INVALID:
    ?landParcel obj:HasFloodRisk ?scenario .
    
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
    obj:GeoObject-code - A string literal, defines the code of the GeoObject. Code is the uniqueness constraint for this dataset.
    obj:GeoObject-uid - A generated, unique UUID for the object. Should not be shown to the end user as it does not contain much significance. Use code instead.
    obj:GeoObject-dataSource - A uri reference to the object's origin DataSource. May be used in combination with DataSource and SourceAuthority joins to show provenance information.
    
    
    The following types contain domain specific attribution, where the type is listed first and an iri for the predicate which links to the literal is listed second, and then a description of the attribute is listed third.
    
    obj:LandParcel, obj:LandParcel-mailAddress, String literal. Specifies the mail address the land parcel
    obj:LandParcel, obj:LandParcel-landUse, String literal. Specifies the use of land parcel
    obj:LandParcel, obj:LandParcel-lotAcres, Number. Specifies the total acres of the parcel
    obj:Structure, obj:Structure-valStruct, Number. Value of the physical structure
    obj:Structure, obj:Structure-valContents, Number. Value of the contents inside of the structure
    
    Additionally, there are four population metrics you can use to answer questions about the population:
    
    obj:Structure, obj:Structure-pop2amu65, Number. Population at night for the structure of people under the age of 65
    obj:Structure, obj:Structure-pop2amo65, Number. Population at night for the structure of people over the age of 65
    obj:Structure, obj:Structure-pop2pmu65, Number. Population during the day for the structure of people under the age of 65
    obj:Structure, obj:Structure-pop2pmo65, Number. Population during the day for the structure of people over the age of 65
    
    The population at night and during the day are mutually exclusive and cannot be aggregated together.  Unless the user specifically asks for the population at night assume they are asking questions about the population during the day.
        """;
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
        
        If the user asks about what Inundation scenarios are available, query obj:FloodScenario and return the results (along with #mapit).
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
            The following is a list of examples intended to showcase how and when to use #mapIt
            =
            
            Q: What is the total population in the flood scenario with a code of '1'?
            A: The total population in the project area with a code of '1' is 200. #mapIt
            
            Q: What is the most common land use for parcels in the project area with a code of 'Example Location'?
            A: The most common land use for parcels in the project area with a code of 'Example Location' is Single Family. #mapIt
            
            - Do not include the #mapIt tag if the question is comparing multiple scenarios.  For example, the following should not include the #mapIt tag:
            Q: What is total value of structures at risk with the no mitigation scenario versus the combo plan mitigation scenario?
            A: The total value of structures at risk with the no mitigation scenario is $1,000 versus the combo plan mitigation scenario which is $50.
            """;
  }
}
