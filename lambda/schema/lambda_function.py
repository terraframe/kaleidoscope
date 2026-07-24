import json
from typing import Any, Dict


SCHEMA = """
The database does NOT include any data in the default graph. When executing queries, you must always specify one or more graphs in the FROM clause, or you may specify a graph wildcard to query all graphs.

Most of this data is centered around flooding usecases. Questions about population can be answered by utilizing the 'population' attribute on CensusTract. Questions about flooded objects (i.e. hospitals or schools) can usually be answered by navigating to a LeveeArea or a LeveedArea and then navigating the relationship to find the affected objects. Finally, there exists InundationArea, which is the result of a flood water inundation analysis expert system, represented as a polygon. By traversing the 'InundatedObject' edge, you can determine which objects were predicted to be 'inundated' (or flooded) based on the flood water inundation analysis.

===
- Self referencing should always generate an edge with *
===
// Incorrect
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
PREFIX lpgvs: <https://localhost:4200/lpg/graph_801104/0/rdfs#> 
PREFIX geo: <http://www.opengis.net/ont/geosparql#>

SELECT DISTINCT ?tractCode ?tractLabel
FROM <https://localhost:4200/lpg/graph_801104/0#>
WHERE {    
 ?parent rdf:type lpgvs:ChannelReach ;
 lpgs:GeoObject-code "CEMVK_RR_03_ONE_27" .    
 ?parent lpgvs:FlowsInto ?channel .    
 ?channel lpgvs:ChannelHasLevee ?leveeArea .    
 ?leveeArea lpgs:GeoObject-code ?leveeAreaCode .    
}

// Correct
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
PREFIX lpgvs: <https://localhost:4200/lpg/graph_801104/0/rdfs#> 
PREFIX geo: <http://www.opengis.net/ont/geosparql#>

SELECT DISTINCT ?tractCode ?tractLabel
FROM <https://localhost:4200/lpg/graph_801104/0#>
WHERE {    
 ?parent rdf:type lpgvs:ChannelReach ;
 lpgs:GeoObject-code "CEMVK_RR_03_ONE_27" .    
 ?parent lpgvs:FlowsInto* ?channel .    
 ?channel lpgvs:ChannelHasLevee ?leveeArea .    
 ?leveeArea lpgs:GeoObject-code ?leveeAreaCode .    
}

===
- Aggregation functions must always be wrapped in parenthesis with its variable name
===
// Incorrect
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
PREFIX lpgvs: <https://localhost:4200/lpg/graph_801104/0/rdfs#>

SELECT SUM(?population) as ?totalPopulation
FROM <https://localhost:4200/lpg/graph_801104/0#>
WHERE {
 ?censusTract lpgs:GeoObject-code "CEMVK_RR_03_ONE_27" .
 ?censusTract lpgvs:CensusTract-population ?population . 
}

// Correct
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
PREFIX lpgvs: <https://localhost:4200/lpg/graph_801104/0/rdfs#>

SELECT (SUM(?population) as ?totalPopulation)
FROM <https://localhost:4200/lpg/graph_801104/0#>
WHERE {
 ?censusTract lpgs:GeoObject-code "CEMVK_RR_03_ONE_27" .
 ?censusTract lpgvs:CensusTract-population ?population . 
}
===
Note: Be as concise as possible.
Do not include any explanations or apologies in your responses.
Do not include any text except the SPARQL query generated.

======
Schema
======

The full schema of the database is provided. For each section, a short description of the schema will be provided, followed by the data. This schema will be used to generate SPARQL queries used to serve end-user requests.

The ‘lpg’ schema often refers to ‘Geo-Object’, a concept coined by TerraFrame. A Geo-Object is a spatial concept, and can be thought of as a more formalized extension of a traditional GIS Feature. A GeoObjectType contains the metadata which defines the concrete GeoObject.

======
Graphs
======
The database does NOT include any data in the default graph. When executing queries, you must always specify one or more graphs in the FROM clause, or you may specify a graph wildcard to query all graphs.

There are two separate graphs in this database:
- <https://localhost:4200/lpg/graph_801104/0#>
- <http://dime.usace.mil/data/dataset#REMIS_PROJECTS>

Never put lpgv and cwbi triples into the same GRAPH block.

The first graph contains a vast wealth of location data, complete with geometries. If your query requires geometries, you will need to query this graph.
The second graph is mostly used for joining data against the first, specifically for usage in a 'project' or 'remis project' context.
The second graph DOES NOT contain project or program labels.

========
Prefixes
========

A full list of the prefixes used for the IRIs within this database.
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

To be safe, always include all of these prefixes in your queries.

=====
Types
=====
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


=====
Edges
=====


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

==========
Attributes
==========


There are many ‘data’ attributes which exist on these types for which various information can be fetched. These data attributes, for example, may define a display label, a code, or even a ‘population’ which might be required to service a particular user query.


rdfs:label - A string literal, defines the label of the GeoObject
lpgs:GeoObjectType-code - A string literal, defines the code of the GeoObject. Code is the uniqueness constraint for this dataset.
lpgs:GeoObjectType-uid - A generated, unique UUID for the object. Should not be shown to the end user as it does not contain much significance. Use code instead.


Geometries for this dataset are stored in accordance with the GeoSPARQL standard and are only available on the lpgv dataset.
?geoObject geo:hasGeometry ?geometry .
?geometry geo:asWKT ?wkt .


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


============
Joining Data
============
The data of type lpgvs:Project is conceptually the same as cwbi:Remis_Project. The only difference is that lpgvs:Project has the geometries whereas Remis_Project does not. Both objects however will have the same code and are conceptually the same:
?proj a lpgvs:Project .
?proj lpgs:GeoObject-code "30000667" .
?remisproj a cwbi:Remis_Project .
?remisproj skos:altLabel "30000667" .

You can therefore start with a cwbi:Program, navigate the cwbi:Program edge to get cwbi:Remis_Project, join that (by code) against lpgvs:Project, and then navigate from there across the lpgvs:ConnectedTo edge to find all sorts of levees and reaches and recreation areas.

All project and program data can be mapped so please return a #mapit it when giving project or program information.


============
Flood Water Inundation
=============
If the user asks 'Which objects are inundated', you ONLY need to consider these objects:
InundationArea -> InundatedObject -> ?object

If the user asks about what Inundation scenarios are available, query InundationArea and return the results (along with #mapIt).

========
Examples
========

===
Show me all objects which are reachable from a program with code 000510
===

PREFIX cwbi: <http://dime.usace.mil/ontologies/cwbi-concept#>
PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
PREFIX lpg: <https://localhost:4200/lpg#>
PREFIX lpgv: <https://localhost:4200/lpg/graph_801104/0#>
PREFIX lpgvs: <https://localhost:4200/lpg/graph_801104/0/rdfs#>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

SELECT ?connected
WHERE {
  BIND("000510" AS ?programCode)

  GRAPH <http://dime.usace.mil/data/dataset#REMIS_PROJECTS> {
    ?program a cwbi:Program ;
             skos:altLabel ?programCode .
    ?rem_proj a cwbi:Remis_Project ;
             cwbi:Program ?program ;
             skos:altLabel ?projCode .
  }

  GRAPH <https://localhost:4200/lpg/graph_801104/0#> {
    ?proj a lpgvs:Project ;
    	lpgvs:Project-programCode ?programCode ;
    	lpgs:GeoObject-code ?projCode .
    ?connected lpgvs:ConnectedTo ?project .
  }
}
LIMIT 100

===
Q: "How much was spent on projects associated with a program with code 000510?" 
A: Costing data for all projects of a specific program can be queried with the following
===

PREFIX cwbi: <http://dime.usace.mil/ontologies/cwbi-concept#>
PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
PREFIX lpg: <https://localhost:4200/lpg#>
PREFIX lpgv: <https://localhost:4200/lpg/graph_801104/0#>
PREFIX lpgvs: <https://localhost:4200/lpg/graph_801104/0/rdfs#>
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

==
Q: How can I see all the project geometries associated with program '000510'?
A: Project geometries can be fetched from the localhost lpg graph, joined across the REMIS graph via the programCode edge
==

PREFIX cwbi: <http://dime.usace.mil/ontologies/cwbi-concept#>
PREFIX lpgs: <https://localhost:4200/lpg/rdfs#>
PREFIX lpg: <https://localhost:4200/lpg#>
PREFIX lpgv: <https://localhost:4200/lpg/graph_801104/0#>
PREFIX lpgvs: <https://localhost:4200/lpg/graph_801104/0/rdfs#>
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

  GRAPH <https://localhost:4200/lpg/graph_801104/0#> {
    ?proj a lpgvs:Project ;
    	lpgs:GeoObject-code ?projCode ;
    	lpgvs:Project-programCode ?code ;
    	rdfs:label ?label ;
    	geo:hasGeometry ?geom .
    ?geom geo:asWKT ?wkt .
  }
}
LIMIT 100

===
Q: "How much was spent on a program with code '000510'?"
Q: "What is the budget for a program with code '000510'?"
Q: "How much budget is remaining on program '000510'?"
A: Budgetary information is stored at the program level and costing (actuals) data is stored at the project level. Costing data can be aggregated up to a program and then compared with the program budget data to gather remaining budget.
===

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

    """


def lambda_handler(event, context):
    return {
        "result": SCHEMA
    }
