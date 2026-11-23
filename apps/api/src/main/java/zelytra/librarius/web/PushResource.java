package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.domain.AppUser;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.PushDeviceDto;
import zelytra.librarius.web.ApiDtos.PushPrefsDto;

/**
 * The caller's push notifications (#69): which categories they opted into, and the devices that
 * receive them. Like the rest of {@code /api/me}, it names no account: a member only ever reaches
 * their own choices, and only ever forgets a device registered to them.
 */
@Path("/api/me/push")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class PushResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    EntityManager em;

    @GET
    public PushPrefsDto get() {
        AppUser user = currentUser.require();
        return new PushPrefsDto(user.pushReleases, user.pushWishlist, user.pushGoal);
    }

    /** Sets the three categories at once — 200 with them, 400 when one is missing. */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public PushPrefsDto update(@Valid @NotNull PushPrefsDto dto) {
        AppUser user = currentUser.require();
        user.pushReleases = dto.releases();
        user.pushWishlist = dto.wishlist();
        user.pushGoal = dto.goal();
        return get();
    }

    /**
     * Registers the device the app runs on — 204. Registering it again refreshes it; a token
     * already registered to another account moves to the caller, who holds the device now.
     */
    @PUT
    @Path("/devices")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public void register(@Valid @NotNull PushDeviceDto dto) {
        currentUser.require();
        em.createNativeQuery("""
                        insert into push_device (token, user_id, platform, updated_at)
                        values (:token, :user, :platform, now())
                        on conflict (token) do update
                        set user_id = excluded.user_id, platform = excluded.platform, updated_at = now()""")
                .setParameter("token", dto.token())
                .setParameter("user", currentUser.id())
                .setParameter("platform", dto.platform())
                .executeUpdate();
    }

    /** Forgets one of the caller's devices — 204; 404 when it is not theirs. */
    @DELETE
    @Path("/devices/{token}")
    @Transactional
    public void forget(@PathParam("token") String token) {
        int removed = em.createNativeQuery("delete from push_device where token = :token and user_id = :user")
                .setParameter("token", token)
                .setParameter("user", currentUser.id())
                .executeUpdate();
        if (removed == 0) {
            throw new NotFoundException();
        }
    }
}
