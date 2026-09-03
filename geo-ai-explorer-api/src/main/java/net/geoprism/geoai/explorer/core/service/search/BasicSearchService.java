package net.geoprism.geoai.explorer.core.service.search;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.util.FmtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.config.AppProperties;
import net.geoprism.geoai.explorer.core.model.Location;
import net.geoprism.geoai.explorer.core.model.LocationPage;
import net.geoprism.geoai.explorer.core.service.GraphQueryService;

/**
 * This class basically provides a SPARQL basic text comparison lookup
 * functionality.
 */
@Service
public class BasicSearchService
{
  @Autowired
  protected GraphQueryService  graph;
  
  @Autowired
  protected AppProperties properties;
  
  protected String getSparqlQuery()
  {
    return """
      SELECT ?uri ?type ?code ?label ?wkt
      FROM <%s>
      WHERE {
        ?uri lpgs:GeoObject-code ?code .
        ?uri rdfs:label ?label .
        ?uri a ?type .

        FILTER(CONTAINS(LCASE(STR(?label)), LCASE(STR(?query))))

        OPTIONAL {
          ?uri geo:hasGeometry ?g .
          ?g geo:asWKT ?wkt .
        }
      }
      ORDER BY ASC(?label)
            """.formatted(properties.getSparqlGraph());
  }

  public LocationPage fullTextLookup(String query, int offset, int limit)
  {
    List<Location> results = new ArrayList<>();

    try (RDFConnection conn = graph.createConnection())
    {
      {
        String sparql = getSparqlQuery() + " LIMIT " + limit + " OFFSET " + offset;

        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setCommandText(sparql);
        pss.setLiteral("query", query);

        try (QueryExecution qe = conn.query(pss.asQuery()))
        {
          ResultSet rs = qe.execSelect();

          while (rs.hasNext())
          {
            QuerySolution qs = rs.next();

            String uri = GraphQueryService.getResourceUri(qs, "uri");
            String type = GraphQueryService.readString(qs, "type");
            String code = GraphQueryService.readString(qs, "code");
            String label = GraphQueryService.readString(qs, "label");
            String wkt = GraphQueryService.readString(qs, "wkt");

            results.add(new Location(uri, type, code, label, GraphQueryService.parseGeometry(wkt)));
          }
        }
      }

      LocationPage page = new LocationPage();
      page.setLocations(results);
      page.setCount(results.size());
      page.setLimit(limit);
      page.setOffset(offset);
      page.setStatement(getSparqlQuery().replace("?query", FmtUtils.stringForString(query)));

      return page;
    }
  }
}
