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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import net.geoprism.geoai.explorer.core.config.AppProperties;
import net.geoprism.geoai.explorer.core.model.Graph;
import net.geoprism.geoai.explorer.core.model.Location;
import net.geoprism.geoai.explorer.core.model.LocationPage;
import net.geoprism.geoai.explorer.core.service.GraphQueryService;
import net.geoprism.geoai.explorer.core.service.search.BasicSearchService;
import software.amazon.awssdk.utils.StringUtils;

@RestController
@Validated
public class ExplorerController
{
  @Autowired
  private GraphQueryService graph;
  
  @Autowired
  private BasicSearchService search;
  
  @Autowired
  protected AppProperties    properties;

  @PostMapping("/api/neighbors")
  @ResponseBody
  public ResponseEntity<Graph> neighbors(@RequestBody Map<String, String> request)
  {
    String uri = request.get("uri");

    if (uri == null || uri.isBlank())
    {
      return ResponseEntity.badRequest().build();
    }
    
    String sExclude = request.get("excludedTypes");
    List<String> exclude = new ArrayList<String>();
    if (!StringUtils.isBlank(sExclude)) {
    	exclude = Arrays.asList(sExclude.split(","));
    }

    Graph graph = this.graph.neighbors(uri, exclude);

    return new ResponseEntity<Graph>(graph, HttpStatus.OK);
  }
  
  @PostMapping("/api/full-text-lookup")
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
  
  private static String escapeUri(String uri)
  {
    return uri.replace("\\", "\\\\")
        .replace(">", "%3E");
  }
  
  private int parseRequestInt(Map<String, String> request, String param, int defaultValue)
  {
	  Integer out = defaultValue;
	  String sObj = request.get(param);
	  
	  if (StringUtils.isNotBlank(sObj))
		  out = Integer.parseInt(sObj);
	  
	  return out;
  }

  @GetMapping("/api/get-attributes")
  @ResponseBody
  public ResponseEntity<Location> neighbors(
      @RequestParam(name = "uri", required = true) String uri, 
      @RequestParam(name = "includeGeometry", required = false, defaultValue = "false") Boolean includeGeometry, 
      @RequestParam(name = "hasPrefix", required = false, defaultValue = "true") Boolean hasPrefix)
  {
    uri = hasPrefix ? uri : properties.getLpgPrefix() + uri;
    
    Location location = this.graph.getAttributes(uri, includeGeometry);

    return new ResponseEntity<Location>(location, HttpStatus.OK);
  }
}
