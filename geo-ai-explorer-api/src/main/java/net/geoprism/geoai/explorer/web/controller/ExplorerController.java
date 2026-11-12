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
package net.geoprism.geoai.explorer.web.controller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import net.geoprism.geoai.explorer.core.config.AppProperties;
import net.geoprism.geoai.explorer.core.model.Graph;
import net.geoprism.geoai.explorer.core.model.JobStatusResponse;
import net.geoprism.geoai.explorer.core.model.Location;
import net.geoprism.geoai.explorer.core.model.LocationPage;
import net.geoprism.geoai.explorer.core.model.PageRequest;
import net.geoprism.geoai.explorer.core.service.AsyncJobService;
import net.geoprism.geoai.explorer.core.service.GraphQueryService;
import net.geoprism.geoai.explorer.core.service.LocationService;
import net.geoprism.geoai.explorer.core.service.search.BasicSearchService;
import software.amazon.awssdk.utils.StringUtils;

@RestController
@Validated
@RequestMapping("/api")
public class ExplorerController
{
  @Autowired
  private GraphQueryService  graph;

  @Autowired
  private BasicSearchService search;

  @Autowired
  private AppProperties      properties;

  @Autowired
  private AsyncJobService    jobs;

  @Autowired
  private LocationService    service;

  @PostMapping("/neighbors")
  @ResponseBody
  public ResponseEntity<Graph> neighbors(@RequestBody Map<String, String> request)
  {
    String uri = request.get("uri");

    if (uri == null || uri.isBlank())
    {
      return ResponseEntity.badRequest().build();
    }

    List<String> exclude = parseExcludedTypes(request);

    Graph graph = this.graph.neighbors(uri, exclude);

    return new ResponseEntity<Graph>(graph, HttpStatus.OK);
  }

  /*
   * neighbors() runs a SPARQL graph query that can be slow for highly-connected
   * nodes -- long enough to exceed the load balancer's 60 second idle timeout
   * and produce a 504 Gateway Timeout. As with the chat endpoints, this kicks
   * the query off in the background (see AsyncJobService) and immediately hands
   * the caller a job id to poll via neighborsStatus() below.
   */
  @PostMapping("/neighbors/start")
  @ResponseBody
  public ResponseEntity<Map<String, String>> startNeighbors(@RequestBody Map<String, String> request)
  {
    String uri = request.get("uri");

    if (uri == null || uri.isBlank())
    {
      return ResponseEntity.badRequest().build();
    }

    List<String> exclude = parseExcludedTypes(request);

    String jobId = this.jobs.submit(() -> this.graph.neighbors(uri, exclude));

    return new ResponseEntity<Map<String, String>>(Map.of("jobId", jobId), HttpStatus.ACCEPTED);
  }

  @GetMapping("/neighbors/status/{jobId}")
  @ResponseBody
  public ResponseEntity<JobStatusResponse> neighborsStatus(@PathVariable(name = "jobId") String jobId)
  {
    return new ResponseEntity<JobStatusResponse>(this.jobs.getStatus(jobId), HttpStatus.OK);
  }

  private List<String> parseExcludedTypes(Map<String, String> request)
  {
    String sExclude = request.get("excludedTypes");

    if (StringUtils.isBlank(sExclude))
    {
      return new ArrayList<String>();
    }

    return Arrays.asList(sExclude.split(","));
  }

  @PostMapping("/full-text-lookup")
  @ResponseBody
  public ResponseEntity<LocationPage> fullTextLookup(@RequestBody Map<String, String> request)
  {
    String query = request.get("query");

    if (query == null || query.isBlank())
    {
      return ResponseEntity.badRequest().build();
    }

    LocationPage locations = this.search.fullTextLookup(query, parseRequestInt(request, "offset", 0), parseRequestInt(request, "limit", 100));

    this.graph.injectGeometries(locations);

    return new ResponseEntity<LocationPage>(locations, HttpStatus.OK);
  }

  private int parseRequestInt(Map<String, String> request, String param, int defaultValue)
  {
    Integer out = defaultValue;
    String sObj = request.get(param);

    if (StringUtils.isNotBlank(sObj))
      out = Integer.parseInt(sObj);

    return out;
  }

  @GetMapping("/get-attributes")
  @ResponseBody
  public ResponseEntity<Location> neighbors(@RequestParam(name = "uri", required = true) String uri, @RequestParam(name = "includeGeometry", required = false, defaultValue = "false") Boolean includeGeometry, @RequestParam(name = "hasPrefix", required = false, defaultValue = "true") Boolean hasPrefix)
  {
    uri = hasPrefix ? uri : properties.getLpgPrefix() + uri;

    Location location = this.graph.getAttributes(uri, includeGeometry);

    return new ResponseEntity<Location>(location, HttpStatus.OK);
  }

  @PostMapping("/get-page")
  @ResponseBody
  public ResponseEntity<LocationPage> getPage(@RequestBody PageRequest page)
  {
    LocationPage response = this.service.getPage(page.getStatement(), page.getType(), page.getOffset(), page.getLimit(), page.getExcludedTypes(), page.getSortField(), page.getSortDirection());

    return new ResponseEntity<LocationPage>(response, HttpStatus.OK);
  }

  @PostMapping("/export-page")
  @ResponseBody
  public ResponseEntity<byte[]> exportPage(@RequestBody PageRequest page)
  {
    String csv = this.service.exportPageCsv(page.getStatement(), page.getType(), page.getExcludedTypes(), page.getSortField(), page.getSortDirection());
    String filename = sanitizeFilename(page.getType() == null || page.getType().isBlank() ? "results" : page.getType()) + ".csv";

    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"").contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(csv.getBytes(StandardCharsets.UTF_8));
  }

  private static String sanitizeFilename(String filename)
  {
    return filename.replaceAll("^.*[#/]", "").replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
  }

}
