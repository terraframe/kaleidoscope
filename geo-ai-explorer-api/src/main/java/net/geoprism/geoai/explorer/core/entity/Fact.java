package net.geoprism.geoai.explorer.core.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "fact")
public class Fact
{

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long    id;

  @ManyToOne
  @JoinColumn(name = "project", nullable = false)
  private Project project;

  @Column(name = "name", nullable = false, unique = false)
  private String  name;

  @Column(name = "description", nullable = false, unique = false)
  @Lob
  private String  description;

  @Lob
  private String  text;

  @Lob
  private String  statements;

  @Lob
  @Column(name = "map_statement")
  private String  mapStatement;

  @CreationTimestamp
  @Column(name = "created_date", nullable = false, updatable = false)
  private Instant createdDate;

}
