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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import net.geoprism.geoai.explorer.core.model.JobStatusResponse;
import net.geoprism.geoai.explorer.core.model.LocationPage;
import net.geoprism.geoai.explorer.core.model.Message;
import net.geoprism.geoai.explorer.core.model.PageRequest;
import net.geoprism.geoai.explorer.core.model.PromptRequest;
import net.geoprism.geoai.explorer.core.service.AsyncJobService;
import net.geoprism.geoai.explorer.core.service.ChatService;

@RestController
@Validated
public class ChatController
{
  @Autowired
  private ChatService service;

  @Autowired
  private AsyncJobService jobs;

  @PostMapping("/api/chat/prompt")
  @ResponseBody
  public ResponseEntity<Message> prompt(@RequestBody PromptRequest request)
  {
    Message response = this.service.prompt(request.getSessionId(), request.getPrompt());

    return new ResponseEntity<Message>(response, HttpStatus.OK);
  }

  /*
   * The chat agent invokes a Bedrock AgentCore harness that can take up to
   * several minutes to respond -- well beyond the load balancer's 60
   * second idle timeout, which was causing 504 Gateway Timeout errors on
   * this endpoint. Rather than blocking the request thread, this kicks the
   * work off in the background (see AsyncJobService) and immediately hands
   * the caller a job id to poll via promptStatus() below.
   */
  @PostMapping("/api/chat/prompt/start")
  @ResponseBody
  public ResponseEntity<Map<String, String>> startPrompt(@RequestBody PromptRequest request)
  {
    String jobId = this.jobs.submit(() -> this.service.prompt(request.getSessionId(), request.getPrompt()));

    return new ResponseEntity<Map<String, String>>(Map.of("jobId", jobId), HttpStatus.ACCEPTED);
  }

  @GetMapping("/api/chat/prompt/status/{jobId}")
  @ResponseBody
  public ResponseEntity<JobStatusResponse> promptStatus(@PathVariable(name = "jobId") String jobId)
  {
    return new ResponseEntity<JobStatusResponse>(this.jobs.getStatus(jobId), HttpStatus.OK);
  }

  @PostMapping("/api/chat/get-page")
  @ResponseBody
  public ResponseEntity<LocationPage> getPage(@RequestBody PageRequest page)
  {
    LocationPage response = this.service.getPage(page.getStatement(), page.getType(), page.getOffset(), page.getLimit(), page.getExcludedTypes(), page.getSortField(), page.getSortDirection());

    return new ResponseEntity<LocationPage>(response, HttpStatus.OK);
  }

  @PostMapping("/api/chat/export-page")
  @ResponseBody
  public ResponseEntity<byte[]> exportPage(@RequestBody PageRequest page)
  {
    String csv = this.service.exportPageCsv(page.getStatement(), page.getType(), page.getExcludedTypes(), page.getSortField(), page.getSortDirection());
    String filename = sanitizeFilename(page.getType() == null || page.getType().isBlank() ? "results" : page.getType()) + ".csv";

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csv.getBytes(StandardCharsets.UTF_8));
  }

  private static String sanitizeFilename(String filename)
  {
    return filename
        .replaceAll("^.*[#/]", "")
        .replaceAll("[^A-Za-z0-9._-]+", "_")
        .replaceAll("_+", "_")
        .replaceAll("^_|_$", "");
  }

  /*
   * See startPrompt() above -- getLocations() also invokes a Bedrock
   * AgentCore harness (to translate the chat history into a SPARQL
   * statement) and can exceed the load balancer's 60 second timeout, so it
   * gets the same background-job treatment.
   */
  @PostMapping("/api/chat/report/start")
  @ResponseBody
  public ResponseEntity<Map<String, String>> startReport(@RequestBody PromptRequest request)
  {
    String jobId = this.jobs.submit(() -> this.service.report(request.getSessionId(), request.getPrompt()));

    return new ResponseEntity<Map<String, String>>(Map.of("jobId", jobId), HttpStatus.ACCEPTED);
  }

  @GetMapping("/api/chat/report/status/{jobId}")
  @ResponseBody
  public ResponseEntity<JobStatusResponse> getReportStatus(@PathVariable(name = "jobId") String jobId)
  {
    return new ResponseEntity<JobStatusResponse>(this.jobs.getStatus(jobId), HttpStatus.OK);
  }


}
