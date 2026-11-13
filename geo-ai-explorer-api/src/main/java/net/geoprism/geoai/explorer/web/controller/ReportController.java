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

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.geoprism.geoai.explorer.core.model.JobStatusResponse;
import net.geoprism.geoai.explorer.core.model.ReportSelection;
import net.geoprism.geoai.explorer.core.service.AsyncJobService;
import net.geoprism.geoai.explorer.core.service.ReportService;

@RestController
@Validated
@RequestMapping("/api/report")
public class ReportController
{
  @Autowired
  private ReportService   service;

  @Autowired
  private AsyncJobService jobs;

  /*
   * Report generation invokes a Bedrock AgentCore harness that can take
   * several minutes to respond -- well beyond the load balancer's 60 second
   * idle timeout. Rather than blocking the request thread, this kicks the
   * work off in the background (see AsyncJobService) and immediately hands
   * the caller a job id to poll via generateStatus() below.
   */
  @PostMapping("/generate/start")
  @ResponseBody
  public ResponseEntity<Map<String, String>> startGenerate(@Valid @RequestBody ReportSelection selection)
  {
    String jobId = this.jobs.submit(() -> this.service.generate(selection));

    return new ResponseEntity<Map<String, String>>(Map.of("jobId", jobId), HttpStatus.ACCEPTED);
  }

  @GetMapping("/generate/status/{jobId}")
  @ResponseBody
  public ResponseEntity<JobStatusResponse> generateStatus(@PathVariable(name = "jobId") String jobId)
  {
    return new ResponseEntity<JobStatusResponse>(this.jobs.getStatus(jobId), HttpStatus.OK);
  }

  /*
   * Once generateStatus() reports SUCCEEDED, the finished report is
   * downloaded from here as a PDF (it is left out of the status payload).
   */
  @GetMapping("/generate/result/{jobId}")
  public ResponseEntity<byte[]> generateResult(@PathVariable(name = "jobId") String jobId)
  {
    byte[] pdf = this.jobs.getResult(jobId, byte[].class);

    return ResponseEntity.ok() //
        .contentType(MediaType.APPLICATION_PDF) //
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("report.pdf").build().toString()) //
        .body(pdf);
  }
}
