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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import net.geoprism.geoai.explorer.core.model.Style;
import net.geoprism.geoai.explorer.core.model.VectorLayer;
import net.geoprism.geoai.explorer.core.service.ConfigurationService;
import net.geoprism.geoai.explorer.core.service.ReportTemplateService;
import net.geoprism.geoai.explorer.web.model.Configuration;

@RestController
@Validated
public class ConfigurationController
{
  @Autowired
  private ConfigurationService service;

  @Autowired
  private ReportTemplateService templateService;

  @GetMapping("/api/configuration/get")
  @ResponseBody
  public ResponseEntity<Configuration> getConfiguration() throws IOException
  {
    Configuration configuration = new Configuration();
    configuration.setStyles(this.service.getStyles());
    configuration.setLayers(this.service.getVectorLayers());
    configuration.setReportTemplates(this.templateService.getAll());

    return new ResponseEntity<Configuration>(configuration, HttpStatus.OK);
  }

  @GetMapping("/api/configuration/styles")
  @ResponseBody
  public ResponseEntity<Map<String, Style>> getStyles() throws IOException
  {
    return new ResponseEntity<Map<String, Style>>(this.service.getStyles(), HttpStatus.OK);
  }

  @GetMapping("/api/configuration/vector-layers")
  @ResponseBody
  public ResponseEntity<List<VectorLayer>> getVectorLayerConfiguration() throws IOException
  {
    return new ResponseEntity<List<VectorLayer>>(this.service.getVectorLayers(), HttpStatus.OK);
  }

}
