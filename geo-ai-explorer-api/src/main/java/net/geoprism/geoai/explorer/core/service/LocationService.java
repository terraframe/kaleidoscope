package net.geoprism.geoai.explorer.core.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.model.GenericRestException;
import net.geoprism.geoai.explorer.core.model.Location;
import net.geoprism.geoai.explorer.core.model.LocationPage;

@Service
public class LocationService
{
  private static final Logger log = LoggerFactory.getLogger(LocationService.class);

  @Autowired
  private GraphQueryService   graph;

  public LocationPage getPage(String statement, String type, int offset, int limit)
  {
    return this.getPage(statement, type, offset, limit, List.of());
  }

  public LocationPage getPage(String statement, String type, int offset, int limit, List<String> excludedTypes)
  {
    return this.getPage(statement, type, offset, limit, excludedTypes, null, null);
  }

  public LocationPage getPage(String statement, String type, int offset, int limit, List<String> excludedTypes, String sortField, String sortDirection)
  {
    try
    {
      boolean combinedPage = type == null || type.isBlank();
      String pageStatement = combinedPage ? this.graph.buildExcludedTypesQuery(statement, excludedTypes) : this.graph.buildTypeFilterQuery(statement, type);

      LocationPage page = new LocationPage();
      page.setType(combinedPage ? null : type);
      page.setLocations(this.graph.query(pageStatement, offset, limit, sortField, sortDirection));
      page.setCount(this.graph.getCount(pageStatement));
      page.setLimit(limit);
      page.setOffset(offset);
      page.setStatement(statement);
      page.setSortField(sortField);
      page.setSortDirection(sortDirection);

      if (combinedPage)
      {
        page.setAvailableTypes(this.graph.getTypeCounts(statement));
      }
      else
      {
        this.graph.injectAttributes(page);
      }

      return page;
    }
    catch (Exception e)
    {
      log.error("Error invoking a remote service: ", e);
      log.error("SPARQL statement: " + statement);

      throw new GenericRestException("Unable to map the locations. An error occurred while generating the response", e);
    }
  }

  public String exportPageCsv(String statement, String type, List<String> excludedTypes, String sortField, String sortDirection)
  {
    boolean combinedPage = type == null || type.isBlank();
    String pageStatement = combinedPage ? this.graph.buildExcludedTypesQuery(statement, excludedTypes) : this.graph.buildTypeFilterQuery(statement, type);
    long count = this.graph.getCount(pageStatement);
    int limit = Math.toIntExact(Math.min(count, 100000L));

    LocationPage page = new LocationPage();
    page.setLocations(this.graph.query(pageStatement, 0, limit, sortField, sortDirection));

    if (!combinedPage)
    {
      this.graph.injectAttributes(page);
    }

    return toCsv(page.getLocations());
  }

  private String toCsv(List<Location> locations)
  {
    List<Location> rows = locations == null ? List.of() : locations;
    Set<String> columns = rows.stream().flatMap(location -> location.getProperties().keySet().stream()).filter(this::isExportableColumn).collect(Collectors.toCollection(LinkedHashSet::new));

    StringBuilder csv = new StringBuilder();
    csv.append(columns.stream().map(this::escapeCsv).collect(Collectors.joining(","))).append("\n");

    for (Location location : rows)
    {
      csv.append(columns.stream().map(column -> escapeCsv(location.getProperties().get(column))).collect(Collectors.joining(","))).append("\n");
    }

    return csv.toString();
  }

  private boolean isExportableColumn(String column)
  {
    return column != null && !Set.of("wkt", "geometry", "geom", "the_geom", "bbox", "edges").contains(column);
  }

  private String escapeCsv(Object value)
  {
    if (value == null)
    {
      return "";
    }

    String text = String.valueOf(value);
    boolean mustQuote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");

    text = text.replace("\"", "\"\"");

    return mustQuote ? "\"" + text + "\"" : text;
  }

}
