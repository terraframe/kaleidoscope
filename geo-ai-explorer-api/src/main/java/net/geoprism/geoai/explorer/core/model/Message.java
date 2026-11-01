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

}
