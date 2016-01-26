package morecat.api.public_;

import morecat.api.helper.MetaEntry;
import morecat.api.helper.MetaSiblingEntry;
import morecat.api.helper.MetaSingleEntry;
import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.SiblingEntry;
import morecat.domain.SinglePage;
import morecat.domain.model.Entry;
import morecat.domain.service.EntryService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

/**
 * @author Yoshimasa Tanabe
 */
@ApplicationScoped
@Path("/entries")
public class EntryController {

  @Inject
  private EntryService entryService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Page<MetaEntry> getPublishedEntries(@QueryParam("size") @DefaultValue("5") int size,
                                      @QueryParam("page") @DefaultValue("0") int page) {

    Page<Entry> publishedEntries = entryService.findPublishedEntries(new Pageable(size, page));
    return publishedEntries.convert(MetaEntry.from(publishedEntries.getElements()));
  }

  @GET
  @Path("/{year}/{month}/{day}/{permalink}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getPublishedEntry(@PathParam("year") int year,
                      @PathParam("month") int month,
                      @PathParam("day") int day,
                      @PathParam("permalink") String permalink) {

    Optional<SinglePage<Entry, SiblingEntry>> publishedSingleEntry = entryService.findPublishedSingleEntry(year, month, day, permalink);

    if (! publishedSingleEntry.isPresent()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    SinglePage<Entry, SiblingEntry> published = publishedSingleEntry.get();

    return Response.ok(published.convert(
      MetaSingleEntry.from(published.getElement()),
      (published.getNext() != null) ? MetaSiblingEntry.from(published.getNext()) : null,
      (published.getPrevious() != null) ? MetaSiblingEntry.from(published.getPrevious()) : null
    )).build();
  }

}
