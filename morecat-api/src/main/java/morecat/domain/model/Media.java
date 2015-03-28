package morecat.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Yoshimasa Tanabe
 */
@Entity
@Table(
  name = "media",
  uniqueConstraints = @UniqueConstraint(columnNames = {"uuid", "created_time"})
)
@Data
@EqualsAndHashCode(callSuper = false)
public class Media extends BaseEntity {

  @Column(nullable = false)
  @NotNull
  private String uuid;

  @Column(nullable = false)
  @NotNull
  private String name;

  @Lob
  @Column(nullable = false)
  @NotNull
  private byte[] content;

  /**
   * Keycloak user name
   */
  @Column(name = "author_name", nullable = false)
  @NotNull
  private String authorName;

  @Column(name = "created_time", nullable = false)
  @NotNull
  private LocalDateTime createdTime;

  @PrePersist
  private void prePersist() {
    setUuid(UUID.randomUUID().toString());
  }

}
