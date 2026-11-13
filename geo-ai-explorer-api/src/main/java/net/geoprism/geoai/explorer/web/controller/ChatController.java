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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import net.geoprism.geoai.explorer.core.model.JobStatusResponse;
import net.geoprism.geoai.explorer.core.model.Message;
import net.geoprism.geoai.explorer.core.model.PromptRequest;
import net.geoprism.geoai.explorer.core.service.AsyncJobService;
import net.geoprism.geoai.explorer.core.service.ChatService;

@RestController
@Validated
@RequestMapping("/api/chat")
public class ChatController
{
  @Autowired
  private ChatService service;

  @Autowired
  private AsyncJobService jobs;

  @PostMapping("/prompt")
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
  @PostMapping("/prompt/start")
  @ResponseBody
  public ResponseEntity<Map<String, String>> startPrompt(@RequestBody PromptRequest request)
  {
    String jobId = this.jobs.submit(() -> this.service.prompt(request.getSessionId(), request.getPrompt()));

    return new ResponseEntity<Map<String, String>>(Map.of("jobId", jobId), HttpStatus.ACCEPTED);
  }

  @GetMapping("/prompt/status/{jobId}")
  @ResponseBody
  public ResponseEntity<JobStatusResponse> promptStatus(@PathVariable(name = "jobId") String jobId)
  {
    return new ResponseEntity<JobStatusResponse>(this.jobs.getStatus(jobId), HttpStatus.OK);
  }

}
