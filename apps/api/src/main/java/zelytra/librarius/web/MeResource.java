package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.account.AccountDeletionService;
import zelytra.librarius.account.AccountEraser.Erased;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.AccountDeletionDto;
import zelytra.librarius.web.ApiDtos.MeDto;

/** Profile of the authenticated user (provisioned on the fly when needed). */
@Path("/api/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    AccountDeletionService deletion;

    @GET
    public MeDto me() {
        return MeDto.of(currentUser.require());
    }

    /**
     * Deletes the caller's account: the Keycloak login, and every row that belongs to them
     * (GDPR art. 17).
     *
     * <p>There is no identifier to pass and none is accepted: a caller can only ever delete
     * themselves, which is the one shape of this endpoint that cannot be pointed at somebody
     * else. The shared catalog is untouched — see {@code AccountEraser}.
     *
     * <p>Answers 503 with a reason, and erases <b>nothing</b>, when the identity provider
     * refuses or is not configured.
     */
    @DELETE
    public AccountDeletionDto delete() {
        Erased erased = deletion.delete(currentUser.id());
        return erased == null
                ? new AccountDeletionDto(0, 0, 0, 0, 0)
                : new AccountDeletionDto(erased.libraryItems(), erased.wishlistItems(),
                        erased.goals(), erased.categories(), erased.seriesFollows());
    }
}
