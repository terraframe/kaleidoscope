package net.geoprism.geoai.explorer.core.model;

import java.util.List;

import lombok.Data;

@Data
public class Message
{
  /**
   * Content of the message
   */
  private String       content;

  private String       reasoning;

  /**
   * Session if of the conversation
   */
  private String       sessionId;

  /**
   * Flag indicating if the message represents a mappable message
   */
  private Boolean      mappable;

  private Boolean      ambiguous;

  private String       location;

  private List<String> statements;

  /**
   * SPARQL returning the lowest-level objects behind this answer, with
   * geometries, so the user can visually audit the answer on a map. Written by
   * the chat agent, never executed by it.
   */
  private String       mapStatement;

}
