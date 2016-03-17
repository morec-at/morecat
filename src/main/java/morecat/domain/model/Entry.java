package morecat.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import morecat.util.StringUtils;
import org.hibernate.annotations.Type;
import org.hibernate.validator.constraints.NotEmpty;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.OrderBy;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
  name = "entries",
  uniqueConstraints = @UniqueConstraint(columnNames = {"permalink", "created_date"})
)
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class Entry extends BaseEntity {

  @Column(nullable = false)
  @NotEmpty
  private String title;

  @Lob
  @Type(type="org.hibernate.type.TextType")
  @Column(nullable = false)
  @NotNull
  private String content;

  @Column(nullable = false)
  @NotEmpty(message = "Permalink must not be empty")
  private String permalink;

  @Column(name = "author_name", nullable = false)
  @NotNull
  private String authorName;

  @Column(name = "created_date", nullable = false)
  @NotNull
  private LocalDate createdDate;

  @Column(name = "created_time", nullable = false)
  @NotNull
  private LocalDateTime createdTime;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "tags", joinColumns = @JoinColumn(name = "entry_id"))
  @Column(name = "value")
  @OrderBy
  private Set<String> tags = new HashSet<>();

  @Column(nullable = false)
  @NotNull
  @Enumerated(EnumType.STRING)
  private EntryState state;

  @Column(nullable = false)
  @NotNull
  @Enumerated(EnumType.STRING)
  private EntryFormat format;

  public Entry(String title, String content, String permalink, String authorName, EntryState state, EntryFormat format) {
    this.title = title;
    this.content = content;
    this.permalink = permalink;
    this.authorName = authorName;
    this.state = state;
    this.format = format;
  }

  @PrePersist
  private void prePersist() {
    setRandomPermalinkIfNotSet();
    setCreatedDate(LocalDate.now());
    setCreatedTime(LocalDateTime.now());
  }

  @PreUpdate
  private void preUpdate() {
    setRandomPermalinkIfNotSet();
  }

  private void setRandomPermalinkIfNotSet() {
    if (StringUtils.isBlank(getPermalink())) {
      try {
        String randomPermalink = String.valueOf(Math.abs(SecureRandom.getInstance("NativePRNGNonBlocking").nextInt()));
        setPermalink(randomPermalink);
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }
  }

}
