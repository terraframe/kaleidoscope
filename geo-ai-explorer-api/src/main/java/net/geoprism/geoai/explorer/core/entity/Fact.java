package net.geoprism.geoai.explorer.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
  private String  description;

  private String  text;

  private String  statements;

}
