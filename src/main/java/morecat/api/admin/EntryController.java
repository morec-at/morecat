package morecat.api.admin;

import morecat.MoreCatLogger;
import morecat.api.admin.helper.MetaEntry;
import morecat.api.admin.helper.MetaSingleEntry;
import morecat.domain.Page;
import morecat.domain.Pageable;
import morecat.domain.model.Entry;
import morecat.domain.model.EntryFormat;
import morecat.domain.model.EntryState;
import morecat.domain.service.EntryService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * @author Yoshimasa Tanabe
 */
@ApplicationScoped
@Path("/admin/entries")
public class EntryController {

  @Inject
  private EntryService entryService;

  // FIXME only admin can use this api.
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Page<MetaEntry> findAllByAdmin(@QueryParam("size") @DefaultValue("5") int size,
                                    @QueryParam("page") @DefaultValue("0") int page) {
    Page<Entry> allAuthorsEntries = entryService.findAllByAdmin(new Pageable(size, page));
    return allAuthorsEntries.convert(MetaEntry.from(allAuthorsEntries.getElements()));
  }

  @GET
  @Path("/{author}")
  @Produces(MediaType.APPLICATION_JSON)
  public Page<MetaEntry> findAllByAuthor(@PathParam("author") String authorName,
                                     @QueryParam("size") @DefaultValue("5") int size,
                                     @QueryParam("page") @DefaultValue("0") int page) {
    Page<Entry> authorsEntries = entryService.findAllByAuthor(new Pageable(size, page), authorName);
    return authorsEntries.convert(MetaEntry.from(authorsEntries.getElements()));
  }

  @GET
  @Path("/{id:[0-9][0-9]*}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response find(@PathParam("id") long id) {
    Entry entry = entryService.find(id);
    if (entry == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    return Response.ok(MetaSingleEntry.from(entry)).build();
  }

  @GET
  @Path("/available-formats")
  @Produces(MediaType.APPLICATION_JSON)
  public EntryFormat[] getAvailableFormats() {
    return EntryFormat.values();
  }

  @GET
  @Path("/available-states")
  @Produces(MediaType.APPLICATION_JSON)
  public EntryState[] getAvailableStates() {
    return EntryState.values();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(@Context UriInfo uriInfo, Entry entry) {

    // FIXME login user name
    entry.setAuthorName("dummy");

    Entry created = entryService.save(entry);

    MoreCatLogger.LOGGER.updateEntry(
      created.getCreatedDate().getYear(), created.getCreatedDate().getMonthValue(), created.getCreatedDate().getDayOfMonth(), created.getTitle(), created.getAuthorName());

    return Response
      .created(uriInfo.getBaseUriBuilder()
        .path("entries")
        .path(String.valueOf(created.getCreatedDate().getYear()))
        .path(String.valueOf(created.getCreatedDate().getMonthValue()))
        .path(String.valueOf(created.getCreatedDate().getDayOfMonth()))
        .path(created.getPermalink())
        .build()
      ).build();
  }

  @PUT
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response update(@PathParam("id") long id, Entry entry) {

    Entry present = entryService.find(id);

    if (present == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    present.setTitle(entry.getTitle());
    present.setContent(entry.getContent());
    present.setPermalink(entry.getPermalink());
    present.setTags(entry.getTags());
    present.setState(entry.getState());
    present.setFormat(entry.getFormat());

    Entry updated = entryService.save(present);

    MoreCatLogger.LOGGER.updateEntry(
      updated.getCreatedDate().getYear(), updated.getCreatedDate().getMonthValue(), updated.getCreatedDate().getDayOfMonth(), updated.getPermalink(), updated.getAuthorName());

    return Response.ok().build();
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") long id) {
    Entry present = entryService.find(id);

    if (present == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    entryService.delete(id);
    MoreCatLogger.LOGGER.deleteEntry(
      present.getCreatedDate().getYear(), present.getCreatedDate().getMonthValue(), present.getCreatedDate().getDayOfMonth(), present.getPermalink(), present.getAuthorName());

    return Response.noContent().build();
  }

}
