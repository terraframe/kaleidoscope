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

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import net.geoprism.geoai.explorer.core.model.GenericRestException;
import net.geoprism.geoai.explorer.core.model.Location;
import net.geoprism.geoai.explorer.core.model.LocationPage;
import net.geoprism.geoai.explorer.core.model.Message;

@Service
public class ChatService
{
  private static final Logger log = LoggerFactory.getLogger(ChatService.class);

  @Autowired
  private BedrockService      bedrock;

  @Autowired
  private GraphQueryService   graph;

  public Message prompt(String sessionId, String inputText)
  {
    RetryPolicy<Message> retryPolicy = RetryPolicy.<Message> builder().handle(Exception.class).withMaxAttempts(3).withDelay(Duration.ofMillis(250)).onFailedAttempt(event -> {
      Throwable failure = event.getLastException();

      if (failure instanceof UnableToAssistResponseException)
      {
        log.warn("Chat prompt attempt {} returned an unable-to-assist response. Retrying.", event.getAttemptCount());
      }
      else
      {
        log.warn("Chat prompt attempt {} failed with exception.", event.getAttemptCount(), failure);
      }
    }).build();

    try
    {
      return Failsafe.with(retryPolicy).get(() -> {
        Message message = this.bedrock.prompt(sessionId, inputText);

        if (isUnableToAssistResponse(message))
        {
          throw new UnableToAssistResponseException(message);
        }

        return message;
      });
    }
    catch (UnableToAssistResponseException e)
    {
      log.error("Chat agent returned unable-to-assist response after retries.");

      return e.getMessageResponse();
    }
    catch (Exception e)
    {
      log.error("Error invoking a remote service after retries: ", e);

      throw new GenericRestException("The chat agent was unable to generate a response. If your chat history is not relevant to the current request, you can try clearing your chat history and sending your message again.", e);
    }
  }

  private boolean isUnableToAssistResponse(Message message)
  {
    return message != null && message.getContent() != null && message.getContent().toLowerCase().contains("i am unable to assist");
  }

  private static class UnableToAssistResponseException extends RuntimeException
  {
    private static final long serialVersionUID = -1972800538349812370L;

    private final Message     messageResponse;

    private UnableToAssistResponseException(Message messageResponse)
    {
      super("Chat agent returned unable-to-assist response.");
      this.messageResponse = messageResponse;
    }

    public Message getMessageResponse()
    {
      return messageResponse;
    }
  }

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

  public String report(String sessionId, String inputText)
  {
    RetryPolicy<String> retryPolicy = RetryPolicy.<String> builder().handle(Exception.class).withMaxAttempts(3).withDelay(Duration.ofMillis(250)).onFailedAttempt(event -> {
      Throwable failure = event.getLastException();

      if (failure instanceof UnableToAssistResponseException)
      {
        log.warn("Chat prompt attempt {} returned an unable-to-assist response. Retrying.", event.getAttemptCount());
      }
      else
      {
        log.warn("Chat prompt attempt {} failed with exception.", event.getAttemptCount(), failure);
      }
    }).build();

    try
    {
      return Failsafe.with(retryPolicy).get(() -> {
        return this.bedrock.report(sessionId, inputText);
      });
    }
    catch (Exception e)
    {
      log.error("Error invoking a remote service after retries: ", e);

      throw new GenericRestException("The report agent was unable to generate a response. If your chat history is not relevant to the current request, you can try clearing your chat history and sending your message again.", e);
    }
  }

}
