/*
 * Copyright (c) 2025 TerraFrame
 *
 * Licensed under the MIT License. See the LICENSE file in the project root
 * for full license information.
 */
package net.geoprism.geoai.explorer.core.service.prompt;

public class SharedPrompt
{
  public static String aggregationFunctions()
  {
    return """
    =
    Aggregation functions must always be wrapped in parenthesis with its variable name
    =
    // Incorrect
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX obj: <%1$s>
    
    SELECT SUM(?population) as ?totalPopulation
    FROM <%2$s>
    WHERE {
     ?censusTract obj:GeoObject-code "CEMVK_RR_03_ONE_27" .
     ?censusTract obj:CensusTract-population ?population . 
    }
    
    // Correct
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX obj: <%1$s>
    
    SELECT (SUM(?population) as ?totalPopulation)
    FROM <%2$s>
    WHERE {
     ?censusTract obj:GeoObject-code "CEMVK_RR_03_ONE_27" .
     ?censusTract obj:CensusTract-population ?population . 
    }
    
    =
    Aggregation functions must always restrict the values to their distinct subject
    =
    // Incorrect
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> 
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX obj: <%1$s>
    
    SELECT (SUM(?pop2pmu65) as ?totalPopulation) 
    FROM <%2$s>
    WHERE {
      ?structure obj:GeoObject-code "1443" .
      ?structure obj:Structure-pop2pmu65 ?pop2pmu65 . 
    } 
    
    // Correct
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> 
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX obj: <%1$s>
    
    SELECT (SUM(?pop2pmu65) as ?totalPopulation) 
    FROM <%2$s>
    WHERE {
      SELECT DISTINCT ?structure ?pop2pmu65
      WHERE {      
        ?structure obj:GeoObject-code "1443" .
        ?structure obj:Structure-pop2pmu65 ?pop2pmu65 . 
      }
    }
        """;
  }

  /**
   * SPN: the NSI occupancy type classification of obj:Structure and its
   * obj:OccupancyType concept taxonomy. Shared by the chat and map it prompts.
   */
  public static String spnOccupancyTypes()
  {
    return """
    =
    Occupancy Type
    =
    Every obj:Structure is classified with an NSI (National Structure Inventory) occupancy type: the generic use of the building, such as a single-family home, a retail store or a school. The predicate obj:Structure-occupancyType links a structure to an obj:OccupancyType concept.

    Occupancy types are not locations. They have no geometry, are never returned as map results and must never be wrapped in location links. Show them to the user by their rdfs:label; you may add the code in parentheses, for example "Hospital (COM6)".

    The IRI of each concept is obj:OccupancyType-<code>, for example obj:OccupancyType-RES1-1SNB. Each concept has an rdfs:label. The concepts form a taxonomy: (obj:OccupancyType)->[obj:OccupancyTypeNarrower]->(obj:OccupancyType) points from a broader concept to a narrower one. Structures are only classified with the most specific concepts (the ones with no narrower concepts below them).

    This is the full taxonomy as (code, label) pairs. Each concept is indented beneath its broader concept:

    NSI-OCC, Occupancy Type
      RES, Residential
        RES1, Single-family residential
          RES1-1SNB, Single-family, 1 story, no basement
          RES1-1SWB, Single-family, 1 story, with basement
          RES1-2SNB, Single-family, 2 stories, no basement
          RES1-2SWB, Single-family, 2 stories, with basement
          RES1-3SNB, Single-family, 3 stories, no basement
          RES1-3SWB, Single-family, 3 stories, with basement
        RES3, Multi-family residential
          RES3A, Multi-family, 2 units
          RES3B, Multi-family, 3-4 units
          RES3C, Multi-family, 5-10 units
          RES3D, Multi-family, 10-19 units
          RES3E, Multi-family, 20-50 units
          RES3F, Multi-family, 50+ units
        RES2, Manufactured home
        RES4, Average hotel
        RES5, Institutional dormitory
        RES6, Nursing home
      COM, Commercial
        COM1, Average retail
        COM2, Average wholesale
        COM3, Average personal and repair services
        COM4, Average professional/technical services
        COM5, Bank
        COM6, Hospital
        COM7, Average medical office
        COM8, Average entertainment/recreation
        COM9, Average theater
        COM10, Garage
        AGR1, Average agricultural
      IND, Industrial
        IND1, Average heavy industrial
        IND2, Average light industrial
        IND3, Average food/drug/chemical
        IND4, Average metals/minerals processing
        IND5, Average high technology
        IND6, Average construction
      PUB, Public
        REL1, Church
        GOV1, Average government services
        GOV2, Average emergency response
        EDU1, Average school

    In the RES1 codes, 1S, 2S and 3S are the number of stories, NB means no basement and WB means with basement.

    Map the user's words to concepts using the labels above. Common examples:
    - houses, homes, single-family homes: RES1
    - apartments, multi-family housing: RES3
    - mobile homes, manufactured homes: RES2
    - hotels: RES4; dormitories: RES5; nursing homes: RES6
    - stores, shops, retail: COM1; offices: COM4; banks: COM5; hospitals: COM6; clinics, medical offices: COM7
    - farm buildings, agricultural: AGR1
    - churches, places of worship: REL1; schools: EDU1; government buildings: GOV1; fire stations, police stations, emergency services: GOV2
    - residential, commercial, industrial or public buildings: RES, COM, IND or PUB
    If no concept fits the user's words, say so and list the closest occupancy types instead of guessing.

    To match a concept and everything beneath it, start at that concept's IRI and follow obj:OccupancyTypeNarrower with the * property path. The * also matches the starting concept itself, so the same pattern works for a group (RES1) and for a single code (RES1-1SNB):

      obj:OccupancyType-RES1 obj:OccupancyTypeNarrower* ?occupancyType .
      ?structure obj:Structure-occupancyType ?occupancyType .

    To report the occupancy type of structures, or to group results by it, read the label of the linked concept:

      ?structure obj:Structure-occupancyType ?occupancyType .
      ?occupancyType rdfs:label ?occupancyTypeLabel .
        """;
  }
}
