package net.geoprism.geoai.explorer.core.model;

import java.util.HashMap;
import java.util.Map;

import org.locationtech.jts.geom.Geometry;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.geoprism.geoai.explorer.core.serialization.GeometrySerializer;

@Data
@NoArgsConstructor
public class Location
{
  private Map<String, Object> properties = new HashMap<>();

  private String              type       = "Feature";

  private String              id;

  @JsonSerialize(using = GeometrySerializer.class)
  private Geometry            geometry;

  public Location(String uri, String type, String code, String label, Geometry geometry)
  {
    this.id = uri;
    this.geometry = geometry;

    this.properties.put("uri", uri);
    this.properties.put("type", type);
    this.properties.put("code", code);
    this.properties.put("label", label);
  }

  public void addProperty(String name, Object value)
  {
    this.properties.put(name, value);
  }

}
