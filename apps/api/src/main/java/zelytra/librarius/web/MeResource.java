package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.account.AccountDeletionService;
import zelytra.librarius.account.AccountEraser.Erased;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.AccountDeletionDto;
import zelytra.librarius.web.ApiDtos.MeDto;
import zelytra.librarius.web.ApiDtos.UpdateMeDto;

import java.time.DateTimeException;
import java.time.ZoneId;

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
     * Updates the caller's own profile: display name, interface language and time zone.
     *
     * <p>There is no identifier here either — a caller edits their own {@code app_user} and
     * nobody else's, so the endpoint cannot be pointed at another user, exactly like the
     * deletion below. The write goes through {@link CurrentUser#require()}, which provisions
     * the row on the fly if this is the caller's first authenticated call.
     *
     * <p>{@code displayName} and {@code locale} are validated by Bean Validation; the time
     * zone is optional and, when given, must parse as a {@link ZoneId} — a value that does
     * not is a 400. A blank time zone clears it, back to the client's own zone.
     */
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public MeDto update(@Valid UpdateMeDto dto) {
        AppUser user = currentUser.require();
        user.displayName = dto.displayName().trim();
        user.locale = dto.locale();
        user.timeZone = parseZone(dto.timeZone());
        return MeDto.of(user);
    }

    /** A blank zone clears the preference; anything else must be a valid IANA identifier. */
    private static String parseZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(timeZone.trim()).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone: " + timeZone);
        }
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
