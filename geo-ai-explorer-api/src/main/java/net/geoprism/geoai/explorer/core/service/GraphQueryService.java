/**
 * Copyright 2020 The Department of Interior
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.geoprism.geoai.explorer.core.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.geosparql.implementation.parsers.wkt.WKTReader;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.geoprism.geoai.explorer.core.config.AppProperties;
import net.geoprism.geoai.explorer.core.model.GenericRestException;
import net.geoprism.geoai.explorer.core.model.Graph;
import net.geoprism.geoai.explorer.core.model.Location;
import net.geoprism.geoai.explorer.core.model.LocationPage;
import net.geoprism.geoai.explorer.core.model.TypeSummary;

@Service
public class GraphQueryService
{
  protected String buildPrefixes()
  {
    return """
      PREFIX lpg: <%s>
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      PREFIX geo: <http://www.opengis.net/ont/geosparql#>
      PREFIX spatialF: <http://jena.apache.org/function/spatial#>
      """.formatted(properties.getLpgPrefix());
  }

  protected String buildAttributesQuery()
  {
    return buildPrefixes() + """
      SELECT ?s ?p ?o
      FROM <%s>
      WHERE {
        BIND(?uri as ?s) .
        ?s ?p ?o .
      }
      """.formatted(properties.getSparqlGraph());
  }

  protected String buildAttributesWithGeometryQuery()
  {
    return buildPrefixes() + """
      SELECT *
      FROM <%s>
      WHERE {
        {
          SELECT ?s ?p ?o WHERE {
            BIND(?uri as ?s) .
            ?s ?p ?o .
          }
        }
        UNION
        {
          SELECT ?s ?p ?o WHERE {
            BIND(?uri as ?s) .
            BIND(geo:asWKT as ?p) .

            ?s geo:hasGeometry ?geom .
            ?geom ?p ?o
          }
        }
      }
      """.formatted(properties.getSparqlGraph());
  }

  protected String buildNeighborQuery()
  {
    return """
      PREFIX lpg: <%s>
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      PREFIX geo: <http://www.opengis.net/ont/geosparql#>
      PREFIX spatialF: <http://jena.apache.org/function/spatial#>

      SELECT
      ?gf1 ?ft1 ?f1 ?wkt1 ?lbl1 ?code1 # Source Object
      ?e1 ?ev1 # Outgoing Edge
      ?gf2 ?ft2 ?f2 ?wkt2 ?lbl2 ?code2 # Outgoing Vertex (f1 → f2)
      ?e2 ?ev2 # Incoming Edge
      ?gf3 ?ft3 ?f3 ?wkt3 ?lbl3 ?code3 # Incoming Vertex (f3 → f1)
      FROM <%s>
      WHERE {
          BIND(geo:Feature as ?gf1) .
          BIND(?uri as ?f1) .

          # Source Object
          ?f1 a ?ft1 .
          ?f1 rdfs:label ?lbl1 .
          ?f1 lpg:GeoObject-code ?code1 .

          OPTIONAL {
              ?f1 geo:hasGeometry ?g1 .
              ?g1 geo:asWKT ?wkt1 .
          }

          {
              # Outgoing Relationship
              ?f1 ?e1 ?f2 .
              ?f2 a ?ft2 .
              ###TYPE_FILTER_FILTER1###
              ?f2 rdfs:label ?lbl2 .
              ?f2 lpg:GeoObject-code ?code2 .

              BIND(geo:Feature as ?gf2) .
              BIND(?f2 as ?ev1) .

              OPTIONAL {
                  ?f2 geo:hasGeometry ?g2 .
                  ?g2 geo:asWKT ?wkt2 .
              }
          }
          UNION
          {
              # Incoming Relationship
              ?f3 ?e2 ?f1 .
              ?f3 a ?ft3 .
              ###TYPE_FILTER_FILTER2###
              ?f3 rdfs:label ?lbl3 .
              ?f3 lpg:GeoObject-code ?code3 .

              BIND(geo:Feature as ?gf3) .
              BIND(?f3 as ?ev2) .

              OPTIONAL {
                  ?f3 geo:hasGeometry ?g3 .
                  ?g3 geo:asWKT ?wkt3 .
              }
          }
      }
      LIMIT 100
      """.formatted(properties.getLpgPrefix(), properties.getSparqlGraph());
  }

  protected String buildNeighborMetadataQuery()
  {
    return """
      PREFIX lpg: <%s>
      PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      PREFIX geo: <http://www.opengis.net/ont/geosparql#>

      SELECT ?type (COUNT(DISTINCT ?obj) AS ?count)
      FROM <%s>
      WHERE {
        {
          # Type of source object
          BIND(?uri AS ?obj)
          ?obj a ?type .
          ?obj lpg:GeoObject-code ?code .
        }
        UNION
        {
          # Outgoing object types
          ?uri ?p1 ?obj .
          ?obj a ?type .
          ?obj lpg:GeoObject-code ?code .
        }
        UNION
        {
          # Incoming object types
          ?obj ?p2 ?uri .
          ?obj a ?type .
          ?obj lpg:GeoObject-code ?code .
        }
      }
      GROUP BY ?type
      ORDER BY DESC(?count)
      """.formatted(properties.getLpgPrefix(), properties.getSparqlGraph());
  }

  @Autowired
  protected AppProperties    properties;

  public RDFConnection createConnection()
  {
    RDFConnectionRemoteBuilder builder = RDFConnectionRemote.create() //
        .destination(properties.getSparqlUrl());

    return builder.build();
  }

  public List<Location> query(String statement)
  {
    return this.query(statement, 0, 1000);
  }

  public List<Location> query(String statement, int offset, int limit)
  {
    return this.query(statement, offset, limit, null, null);
  }

  public List<Location> query(String statement, int offset, int limit, String sortField, String sortDirection)
  {
    // The agent sometimes includes formatting text. Just remove it...
    String sparql = normalizeLocationStatement(statement);

    // Remove existing LIMIT and OFFSET clauses (case-insensitive)
    sparql = sparql.replaceAll("(?i)LIMIT\\s+\\d+", "");
    sparql = sparql.replaceAll("(?i)OFFSET\\s+\\d+", "");

    sparql = applySort(sparql, sortField, sortDirection);

    // Append new LIMIT and OFFSET
    sparql += " LIMIT " + limit + " OFFSET " + offset;

    try (RDFConnection conn = this.createConnection())
    {
      Map<String, Location> resultsByUri = new LinkedHashMap<>();

      System.out.println("JenaService.query - EXECUTING QUERY");
      System.out.println(sparql);

      conn.querySelect(sparql, (qs) -> {
        String uri = readUri(qs);
        String type = readString(qs, "type");
        String code = readString(qs, "code");
        String label = readString(qs, "label");
        String wkt = readString(qs, "wkt");

        Geometry geometry = null;
        if (StringUtils.isNotBlank(wkt))
        {
          WKTReader reader = WKTReader.extract(wkt);
          geometry = reader.getGeometry();
        }

        Location location = new Location(uri, type, code, label, geometry);

        qs.varNames().forEachRemaining(varName -> {
          if (isCoreLocationField(varName))
          {
            return;
          }

          if (!qs.contains(varName))
          {
            return;
          }

          Object value = readPropertyValue(qs, varName);
          if (value != null)
          {
            location.addProperty(varName, value);
          }
        });

        resultsByUri.putIfAbsent(uri, location);
      });

      return new LinkedList<>(resultsByUri.values());
    }
  }

  private String applySort(String sparql, String sortField, String sortDirection)
  {
    String field = sanitizeSortField(sortField);
    boolean descending = "desc".equalsIgnoreCase(sortDirection);

    if (StringUtils.isBlank(field))
    {
      return sparql + " ORDER BY ASC(?label)";
    }

    String sortedSparql = injectSortValueBinding(sparql, field);
    String direction = descending ? "DESC" : "ASC";

    return sortedSparql + " ORDER BY ASC(!BOUND(?__sortValue)) " + direction + "(?__sortValue) ASC(?label) ASC(?uri)";
  }

  private String sanitizeSortField(String sortField)
  {
    if (StringUtils.isBlank(sortField))
    {
      return null;
    }

    String field = sortField.trim();
    field = field.replaceFirst("^.*[#/]", "");

    if (field.contains("-"))
    {
      field = field.substring(field.lastIndexOf('-') + 1);
    }

    return field.replaceAll("[^A-Za-z0-9_]", "");
  }

  private boolean isSafeSparqlVariableName(String field)
  {
    return Pattern.matches("[A-Za-z_][A-Za-z0-9_]*", field);
  }

  private String injectSortValueBinding(String sparql, String field)
  {
    int whereIndex = indexOfKeyword(sparql, "WHERE");

    if (whereIndex == -1)
    {
      return sparql;
    }

    int openBrace = sparql.indexOf('{', whereIndex);

    if (openBrace == -1)
    {
      return sparql;
    }

    int closeBrace = findMatchingBrace(sparql, openBrace);

    if (closeBrace == -1)
    {
      return sparql;
    }

    String projectedValue = isSafeSparqlVariableName(field)
        ? "?" + field + ", "
        : "";

    String binding = "\n  OPTIONAL {\n" +
        "    ?uri ?__sortPredicate ?__sortAttributeValue .\n" +
        "    BIND(REPLACE(STR(?__sortPredicate), \"^.*[#/]\", \"\") AS ?__sortPredicateLocal) .\n" +
        "    BIND(REPLACE(?__sortPredicateLocal, \"^.*-\", \"\") AS ?__sortPredicateName) .\n" +
        "    FILTER(?__sortPredicateName = \"" + escapeSparqlString(field) + "\") .\n" +
        "  }\n" +
        "  BIND(COALESCE(" + projectedValue + "?__sortAttributeValue) AS ?__sortValue) .\n";

    return sparql.substring(0, closeBrace) + binding + sparql.substring(closeBrace);
  }

  private String readUri(QuerySolution qs)
  {
    // 1. Prefer conventional names first
    List<String> preferredNames = List.of("uri", "resource", "subject", "s", "id", "location", "object");

    for (String name : preferredNames)
    {
      String uri = readUriIfPresent(qs, name);
      if (StringUtils.isNotBlank(uri))
      {
        return uri;
      }
    }

    // 2. Fall back to discovering the first URI resource
    Iterator<String> varNames = qs.varNames();

    while (varNames.hasNext())
    {
      String varName = varNames.next();

      if (isLikelyNonPrimaryUriField(varName))
      {
        continue;
      }

      String uri = readUriIfPresent(qs, varName);
      if (StringUtils.isNotBlank(uri))
      {
        return uri;
      }
    }

    throw new IllegalArgumentException("Could not discover URI field in query result. Available variables: " + getAvailableVariableNames(qs));
  }

  private String readUriIfPresent(QuerySolution qs, String varName)
  {
    if (!qs.contains(varName))
    {
      return null;
    }

    RDFNode node = qs.get(varName);

    if (node == null || !node.isResource())
    {
      return null;
    }

    Resource resource = node.asResource();

    if (!resource.isURIResource())
    {
      return null;
    }

    return resource.getURI();
  }

  private boolean isLikelyNonPrimaryUriField(String varName)
  {
    return switch (varName)
    {
      case "type", "class", "geometry", "geom", "wkt", "label", "code" -> true;
      default -> false;
    };
  }

  private List<String> getAvailableVariableNames(QuerySolution qs)
  {
    List<String> names = new ArrayList<>();
    qs.varNames().forEachRemaining(names::add);
    return names;
  }

  private boolean isCoreLocationField(String varName)
  {
    return switch (varName)
    {
      case "uri", "resource", "subject", "s", "id", "location", "object", "type", "code", "label", "wkt", "__sortPredicate", "__sortValue", "__sortAttributeValue", "__sortPredicateLocal", "__sortPredicateName" -> true;
      default -> false;
    };
  }

  private Object readPropertyValue(QuerySolution qs, String varName)
  {
    RDFNode node = qs.get(varName);

    if (node == null)
    {
      return null;
    }

    if (node.isLiteral())
    {
      Literal literal = node.asLiteral();

      Object value = literal.getValue();

      // Jena may return typed values like Integer, BigDecimal, Boolean, etc.
      // Prefer that over stringifying everything.
      return value != null ? value : literal.getString();
    }

    if (node.isResource())
    {
      Resource resource = node.asResource();

      if (resource.isURIResource())
      {
        return resource.getURI();
      }

      return resource.toString();
    }

    return node.toString();
  }

  public Long getCount(String statement)
  {
    Map<String, Long> holder = new HashMap<>();

    statement = normalizeLocationStatement(statement);

    StringBuilder sparql = new StringBuilder();

    int selectIndex = statement.toUpperCase().indexOf("SELECT");
    int fromIndex = statement.toUpperCase().indexOf("FROM");
    int whereIndex = statement.toUpperCase().indexOf("WHERE");
    int groupByIndex = statement.toUpperCase().indexOf("GROUP BY");

    // Prefix section
    sparql.append(statement.substring(0, selectIndex));
    sparql.append("SELECT (COUNT(distinct ?uri) AS ?count)\n");

    if (groupByIndex != -1)
    {
      sparql.append(statement.substring(fromIndex, groupByIndex));
    }
    else if (fromIndex != -1)
    {
      sparql.append(statement.substring(fromIndex));
    }
    else
    {
      sparql.append(statement.substring(whereIndex));
    }

    try (RDFConnection conn = this.createConnection())
    {
      conn.querySelect(sparql.toString(), (qs) -> {
        holder.put("count", qs.getLiteral("count").getLong());
      });

      return holder.getOrDefault("count", 0L);
    }
  }

  public List<TypeSummary> getTypeCounts(String statement)
  {
    List<TypeSummary> results = new ArrayList<>();
    String sparql = buildTypeCountQuery(statement);

    try (RDFConnection conn = this.createConnection())
    {
      conn.querySelect(sparql, (qs) -> {
        results.add(new TypeSummary(readString(qs, "type"), qs.getLiteral("count").getLong()));
      });
    }

    return results;
  }

  public String buildTypeFilterQuery(String statement, String type)
  {
    if (StringUtils.isBlank(type))
    {
      return normalizeLocationStatement(statement);
    }

    return injectWhereFilter(statement, "FILTER(?type = " + sparqlValue(type) + ") .");
  }

  public String buildExcludedTypesQuery(String statement, List<String> excludedTypes)
  {
    if (excludedTypes == null || excludedTypes.isEmpty())
    {
      return normalizeLocationStatement(statement);
    }

    List<String> values = excludedTypes.stream()
        .filter(StringUtils::isNotBlank)
        .map(this::sparqlValue)
        .toList();

    if (values.isEmpty())
    {
      return normalizeLocationStatement(statement);
    }

    return injectWhereFilter(statement, "FILTER(?type NOT IN (" + String.join(", ", values) + ")) .");
  }

  private String buildTypeCountQuery(String statement)
  {
    String clean = normalizeLocationStatement(statement);
    int selectIndex = indexOfKeyword(clean, "SELECT");
    int fromIndex = indexOfKeyword(clean, "FROM");
    int whereIndex = indexOfKeyword(clean, "WHERE");

    if (selectIndex == -1 || whereIndex == -1)
    {
      throw new IllegalArgumentException("Unable to derive type count query from SPARQL statement.");
    }

    StringBuilder sparql = new StringBuilder();
    sparql.append(clean, 0, selectIndex);
    sparql.append("SELECT ?type (COUNT(DISTINCT ?uri) AS ?count)\n");

    if (fromIndex != -1 && fromIndex < whereIndex)
    {
      sparql.append(clean, fromIndex, whereIndex);
    }

    sparql.append(clean.substring(whereIndex));
    sparql.append("\nGROUP BY ?type\nORDER BY DESC(?count)");

    validateSparql(sparql.toString());

    return sparql.toString();
  }

  private String injectWhereFilter(String statement, String filter)
  {
    String clean = normalizeLocationStatement(statement);
    int whereIndex = indexOfKeyword(clean, "WHERE");

    if (whereIndex == -1)
    {
      throw new IllegalArgumentException("Unable to inject type filter into SPARQL statement.");
    }

    int openBrace = clean.indexOf('{', whereIndex);

    if (openBrace == -1)
    {
      throw new IllegalArgumentException("SPARQL statement does not contain a WHERE block.");
    }

    int closeBrace = findMatchingBrace(clean, openBrace);

    if (closeBrace == -1)
    {
      throw new IllegalArgumentException("SPARQL statement contains an unbalanced WHERE block.");
    }

    String sparql = clean.substring(0, closeBrace) + "\n  " + filter + "\n" + clean.substring(closeBrace);

    validateSparql(sparql);

    return sparql;
  }

  private String cleanForDerivedQuery(String statement)
  {
    String sparql = statement.replaceAll("```", "").trim();
    sparql = sparql.replaceAll("(?is)ORDER\\s+BY\\s+.*?(?=LIMIT\\s+\\d+|OFFSET\\s+\\d+|$)", "");
    sparql = sparql.replaceAll("(?i)LIMIT\\s+\\d+", "");
    sparql = sparql.replaceAll("(?i)OFFSET\\s+\\d+", "");

    return sparql.trim();
  }

  public String normalizeLocationStatement(String statement)
  {
    String clean = cleanForDerivedQuery(statement);
    int selectIndex = indexOfKeyword(clean, "SELECT");
    int whereIndex = indexOfKeyword(clean, "WHERE");

    if (selectIndex == -1 || whereIndex == -1)
    {
      return clean;
    }

    int openBrace = clean.indexOf('{', whereIndex);

    if (openBrace == -1)
    {
      return clean;
    }

    int closeBrace = findMatchingBrace(clean, openBrace);

    if (closeBrace == -1)
    {
      return clean;
    }

    String prefixAndDataset = clean.substring(0, selectIndex) +
        "SELECT DISTINCT ?uri ?type ?code ?label ?wkt\n" +
        clean.substring(indexAfterProjection(clean, selectIndex), whereIndex);
    String whereBlock = clean.substring(whereIndex, closeBrace);
    String projection = clean.substring(selectIndex, whereIndex);
    List<String> bindings = buildCoreAliasBindings(projection);

    StringBuilder normalized = new StringBuilder(prefixAndDataset);
    normalized.append(whereBlock);

    if (!bindings.isEmpty())
    {
      normalized.append("\n");
      bindings.forEach(binding -> normalized.append("  ").append(binding).append("\n"));
    }

    normalized.append("}");

    return normalized.toString().trim();
  }

  private int indexAfterProjection(String sparql, int selectIndex)
  {
    int fromIndex = indexOfKeyword(sparql, "FROM");
    int whereIndex = indexOfKeyword(sparql, "WHERE");

    if (fromIndex != -1 && fromIndex < whereIndex)
    {
      return fromIndex;
    }

    return whereIndex;
  }

  private List<String> buildCoreAliasBindings(String projection)
  {
    return List.of("uri", "type", "code", "label", "wkt").stream()
        .map(alias -> buildAliasBinding(projection, alias))
        .filter(StringUtils::isNotBlank)
        .toList();
  }

  private String buildAliasBinding(String projection, String alias)
  {
    String expression = findProjectionExpression(projection, alias);

    if (StringUtils.isBlank(expression) || expression.equals("?" + alias))
    {
      return null;
    }

    return "BIND(" + expression + " AS ?" + alias + ") .";
  }

  private String findProjectionExpression(String projection, String alias)
  {
    Pattern samplePattern = Pattern.compile(
        "\\(\\s*SAMPLE\\s*\\(\\s*(.*?)\\s*\\)\\s+AS\\s+\\?" + Pattern.quote(alias) + "\\s*\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    java.util.regex.Matcher sampleMatcher = samplePattern.matcher(projection);

    if (sampleMatcher.find())
    {
      return sampleMatcher.group(1).trim();
    }

    Pattern aliasPattern = Pattern.compile(
        "\\(\\s*(.*?)\\s+AS\\s+\\?" + Pattern.quote(alias) + "\\s*\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    java.util.regex.Matcher aliasMatcher = aliasPattern.matcher(projection);

    if (aliasMatcher.find())
    {
      return aliasMatcher.group(1).trim();
    }

    Pattern directPattern = Pattern.compile("\\?" + Pattern.quote(alias) + "\\b");
    java.util.regex.Matcher directMatcher = directPattern.matcher(projection);

    if (directMatcher.find())
    {
      return "?" + alias;
    }

    return "?" + alias;
  }

  private int indexOfKeyword(String sparql, String keyword)
  {
    return sparql.toUpperCase().indexOf(keyword);
  }

  private int findMatchingBrace(String value, int openBrace)
  {
    int depth = 0;
    boolean inString = false;
    char stringDelimiter = '\0';

    for (int i = openBrace; i < value.length(); i++)
    {
      char c = value.charAt(i);
      char previous = i > 0 ? value.charAt(i - 1) : '\0';

      if ((c == '"' || c == '\'') && previous != '\\')
      {
        if (!inString)
        {
          inString = true;
          stringDelimiter = c;
        }
        else if (stringDelimiter == c)
        {
          inString = false;
        }
      }

      if (inString)
      {
        continue;
      }

      if (c == '{')
      {
        depth++;
      }
      else if (c == '}')
      {
        depth--;

        if (depth == 0)
        {
          return i;
        }
      }
    }

    return -1;
  }

  private String sparqlValue(String type)
  {
    String trimmed = type.trim();

    if (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
    {
      return "<" + escapeSparqlIri(trimmed) + ">";
    }

    return "\"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private void validateSparql(String sparql)
  {
    QueryFactory.create(sparql);
  }

  public Location getAttributes(final String uri, boolean includeGeometry)
  {
    // if (uri.startsWith("<") && uri.endsWith(">"))
    // {
    // uri = uri.substring(1, uri.length() - 1);
    // }

    try (RDFConnection conn = this.createConnection())
    {
      String statement = includeGeometry ? buildAttributesWithGeometryQuery() : buildAttributesQuery();

      // Use ParameterizedSparqlString to inject the URI safely
      ParameterizedSparqlString pss = new ParameterizedSparqlString();
      pss.setCommandText(statement);
      pss.setIri("uri", uri);

      Location location = new Location();
      location.setId(uri);
      location.addProperty("uri", uri);

      conn.queryResultSet(pss.asQuery(), (resultSet) -> {

        if (!resultSet.hasNext())
        {
          throw new GenericRestException("Unable to find a location with the uri [" + uri + "]");
        }

        resultSet.forEachRemaining(qs -> {

          String attribute = qs.get("p").asResource().getLocalName();

          RDFNode object = qs.get("o");

          if (attribute.equalsIgnoreCase("asWKT"))
          {
            WKTReader reader = WKTReader.extract(object.asLiteral().getString());
            Geometry geometry = reader.getGeometry();

            location.setGeometry(geometry);
          }
          else if (object.isLiteral())
          {
            // TODO: Use metadata if we have it for attribute names
            // For now assume the attribute is in the style of
            // ClassName-AttributeName
            if (attribute.contains("-"))
            {
              attribute = attribute.split("-")[1];
            }

            Object value = object.asLiteral().getValue();

            location.addProperty(attribute, value);
          }
          else if (object.isResource())
          {
            if (attribute.equalsIgnoreCase("type"))
            {
              location.addProperty("type", object.asResource().getURI());
            }
          }
        });
      });

      return location;

    }

  }

  public Graph neighbors(String uri, List<String> excludedTypes)
  {
    if (uri.startsWith("<") && uri.endsWith(">"))
    {
      uri = uri.substring(1, uri.length() - 1);
    }

    final Graph results = new Graph();

    // Fetch metadata
    try (RDFConnection conn = this.createConnection())
    {
      ParameterizedSparqlString pss = new ParameterizedSparqlString();
      pss.setCommandText(buildNeighborMetadataQuery());
      pss.setIri("uri", uri);

      conn.querySelect(pss.asQuery(), (qs) -> {
        results.getTypeCount().put(readString(qs, "type"), qs.getLiteral("count").getInt());
      });
    }

    // Grab the data
    try (RDFConnection conn = this.createConnection())
    {
      String q = buildNeighborQuery().replace("###TYPE_FILTER_FILTER1###", exclusionFor("?ft2", excludedTypes)).replace("###TYPE_FILTER_FILTER2###", exclusionFor("?ft3", excludedTypes));

      ParameterizedSparqlString pss = new ParameterizedSparqlString();
      pss.setCommandText(q);
      pss.setIri("uri", uri);

      try (QueryExecution qe = conn.query(pss.asQuery()))
      {
        ResultSet rs = qe.execSelect();
        SparqlGraphConverter.convert(results, rs);
      }
    }

    // The object has no neighbors
    if (results.getNodes().size() == 0)
    {
      results.setNodes(Arrays.asList(this.getAttributes(uri, true)));
    }

    return results;
  }

  public void injectGeometries(LocationPage locations)
  {
    if (locations == null || locations.getLocations() == null)
    {
      return;
    }

    List<Location> missingGeometry = locations.getLocations().stream().filter(location -> location.getGeometry() == null).filter(location -> location.getId() != null && !location.getId().isBlank()).toList();

    if (missingGeometry.isEmpty())
    {
      return;
    }

    Map<String, Location> locationsById = locations.getLocations().stream().filter(location -> location.getId() != null).collect(Collectors.toMap(Location::getId, Function.identity(), (a, b) -> a));

    int batchSize = 5;

    for (int i = 0; i < missingGeometry.size(); i += batchSize)
    {
      int end = Math.min(i + batchSize, missingGeometry.size());
      List<Location> batch = missingGeometry.subList(i, end);

      String sparql = buildGeometryLookupQuery(batch);

      for (Location location : this.query(sparql))
      {
        Location match = locationsById.get(location.getId());

        if (match != null && location.getGeometry() != null)
        {
          match.setGeometry(location.getGeometry());
        }
      }
    }
  }

  public void injectAttributes(LocationPage locations)
  {
    if (locations == null || locations.getLocations() == null)
    {
      return;
    }

    Map<String, Location> locationsById = locations.getLocations().stream().filter(location -> location.getId() != null).collect(Collectors.toMap(Location::getId, Function.identity(), (a, b) -> a));
    List<Location> withIds = locations.getLocations().stream().filter(location -> location.getId() != null && !location.getId().isBlank()).toList();

    int batchSize = 50;

    for (int i = 0; i < withIds.size(); i += batchSize)
    {
      int end = Math.min(i + batchSize, withIds.size());
      List<Location> batch = withIds.subList(i, end);
      String sparql = buildAttributeLookupQuery(batch);

      try (RDFConnection conn = this.createConnection())
      {
        conn.querySelect(sparql, (qs) -> {
          String uri = readString(qs, "uri");
          String attribute = readString(qs, "p");

          if (attribute.contains("-"))
          {
            attribute = attribute.split("-")[1];
          }

          if (isCoreLocationField(attribute) || attribute.equalsIgnoreCase("asWKT"))
          {
            return;
          }

          Object value = readPropertyValue(qs, "o");
          Location location = locationsById.get(uri);

          if (location != null && value != null && !location.getProperties().containsKey(attribute))
          {
            location.addProperty(attribute, value);
          }
        });
      }
    }
  }

  private String buildAttributeLookupQuery(List<Location> locations)
  {
    StringBuilder sparql = new StringBuilder();

    sparql.append(buildPrefixes());
    sparql.append("""

        SELECT ?uri ?p ?o
        FROM <%s>
        WHERE {
          VALUES ?uri {
        """.formatted(properties.getSparqlGraph()));

    for (Location location : locations)
    {
      sparql.append("          <").append(escapeSparqlIri(location.getId())).append(">\n");
    }

    sparql.append("""
          }

          ?uri ?p ?o .
          FILTER(?p != geo:hasGeometry)
        }
        """);

    return sparql.toString();
  }

  private String buildGeometryLookupQuery(List<Location> locations)
  {
    StringBuilder sparql = new StringBuilder();

    sparql.append("""
        PREFIX geo: <http://www.opengis.net/ont/geosparql#>

        SELECT ?uri ?wkt
        WHERE {
          VALUES ?uri {
        """);

    for (Location location : locations)
    {
      sparql.append("          <").append(escapeSparqlIri(location.getId())).append(">\n");
    }

    sparql.append("""
          }

          {
            ?uri geo:hasGeometry ?geometry .
            ?geometry geo:asWKT ?wkt .
          }
          UNION
          {
            ?uri geo:asWKT ?wkt .
          }
        }
        """);

    return sparql.toString();
  }

  private static String escapeSparqlIri(String iri)
  {
    return iri.replace("\\", "\\\\").replace(">", "%3E");
  }

  private static String escapeSparqlString(String value)
  {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String exclusionFor(String varName, List<String> excludedTypes)
  {
    if (excludedTypes == null || excludedTypes.isEmpty())
      return "";
    Pattern iriPattern = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s<>\"{}|\\\\^`]*$");

    List<String> safeIris = excludedTypes.stream().filter(t -> t != null && iriPattern.matcher(t).matches()).map(t -> "<" + t + ">").toList();

    if (safeIris.isEmpty())
      return "";
    return "FILTER(" + varName + " NOT IN (" + String.join(", ", safeIris) + ")) .";
  }

  public static String readString(QuerySolution qs, String name)
  {
    if (qs == null || !qs.contains(name))
    {
      return "";
    }

    RDFNode node = qs.get(name);

    if (node == null)
    {
      return "";
    }

    if (node.isLiteral())
    {
      return node.asLiteral().getString();
    }

    if (node.isResource())
    {
      return node.asResource().getURI();
    }

    return node.toString();
  }

  public static String getResourceUri(QuerySolution qs, String name)
  {
    if (qs != null && qs.contains(name) && qs.get(name).isResource())
    {
      return qs.getResource(name).getURI();
    }

    return "";
  }

  public static Geometry parseGeometry(String wkt)
  {
    if (wkt == null || wkt.isBlank())
    {
      return null;
    }

    try
    {
      WKTReader reader = WKTReader.extract(wkt);
      return reader.getGeometry();
    }
    catch (Exception e)
    {
      return null;
    }
  }

}