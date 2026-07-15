package net.geoprism.geoai.explorer.core.service.search;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.geosparql.implementation.parsers.wkt.WKTReader;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.sparql.util.FmtUtils;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.config.AppProperties;
import net.geoprism.geoai.explorer.core.model.Location;
import net.geoprism.geoai.explorer.core.model.LocationPage;
import net.geoprism.geoai.explorer.core.service.GraphQueryService;

/**
 * Uses Jena to provide the full text lookup capabilities
 */
@Service
@Primary
@ConditionalOnProperty(name = "explorer.search", havingValue = "jena")
public class JenaSearchService extends BasicSearchService
{
  
  protected String getSparqlQuery()
  {
    return """
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      PREFIX text: <http://jena.apache.org/text#>
      PREFIX geo: <http://www.opengis.net/ont/geosparql#>
      PREFIX lpg: <%s>

      SELECT ?uri ?type ?code ?label ?wkt
      FROM <%s>
      WHERE {
        (?uri ?score) text:query (rdfs:label ?query) .
        ?uri lpg:GeoObject-code ?code .
        ?uri rdfs:label ?label .
        ?uri a ?type .
        OPTIONAL {
            ?uri geo:hasGeometry ?g .
            ?g geo:asWKT ?wkt .
        }
      }
      ORDER BY DESC(?score)
            """.formatted(properties.getLpgPrefix(), properties.getSparqlGraph());
  }

  public LocationPage fullTextLookup(String query, int offset, int limit)
  {
    List<Location> results = new ArrayList<Location>();

    try (RDFConnection conn = graph.createConnection())
    {
      var sparql = getSparqlQuery();
      sparql += " LIMIT " + limit + " OFFSET " + offset;

      // Use ParameterizedSparqlString to inject the URI safely
      ParameterizedSparqlString pss = new ParameterizedSparqlString();
      pss.setCommandText(sparql);

      pss.setLiteral("query", query);

      try (QueryExecution qe = conn.query(pss.asQuery()))
      {
        ResultSet rs = qe.execSelect();

        while (rs.hasNext())
        {
          QuerySolution qs = rs.next();

          String uri = qs.getResource("uri").getURI();
          String type = qs.getResource("type").getURI();
          String code = qs.getLiteral("code").getString();
          String label = qs.getLiteral("label").getString();
          String wkt = qs.getLiteral("wkt").getString();

          WKTReader reader = WKTReader.extract(wkt);
          Geometry geometry = reader.getGeometry();

          results.add(new Location(uri, type, code, label, geometry));
        }
      }
    }

    LocationPage page = new LocationPage();
    page.setLocations(results);
    page.setCount(results.size());
    page.setLimit(100);
    page.setOffset(0);
    page.setStatement(getSparqlQuery().replace("?query", FmtUtils.stringForString(query)));

    return page;
  }
}
