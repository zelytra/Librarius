package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.domain.GoalUnit;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.ReadingGoal;
import zelytra.librarius.domain.repository.ReadingGoalRepository;
import zelytra.librarius.security.CurrentUser;
import zelytra.librarius.web.ApiDtos.GoalDto;
import zelytra.librarius.web.ApiDtos.GoalUpsertDto;

import java.util.List;

/** The user's yearly reading goals. */
@Path("/api/goals")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class GoalResource {

    @Inject
    CurrentUser currentUser;

    @Inject
    ReadingGoalRepository goals;

    @GET
    public List<GoalDto> list() {
        return goals.listByUser(currentUser.id()).stream().map(GoalDto::of).toList();
    }

    /**
     * Sets the year's goal for one kind of book (#393); {@code BOOK} when the kind is left out,
     * which is what every goal was before goals were per kind.
     */
    @PUT
    @Path("/{year}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public GoalDto upsert(@PathParam("year") int year, @QueryParam("kind") Kind kind,
            @Valid GoalUpsertDto dto) {
        currentUser.require();
        String userId = currentUser.id();
        Kind goalKind = kind != null ? kind : Kind.BOOK;
        ReadingGoal goal = goals.findByUserAndYear(userId, year, goalKind).orElse(null);
        boolean created = goal == null;
        if (created) {
            goal = new ReadingGoal();
            goal.userId = userId;
            goal.year = year;
            goal.kind = goalKind;
        }
        goal.targetCount = dto.targetCount();
        goal.unit = dto.unit() != null ? dto.unit() : GoalUnit.BOOKS;
        // The entity is only persisted once complete: target_count is NOT NULL,
        // so a premature persist() would make the insert fail at commit time.
        if (created) {
            goals.persist(goal);
        }
        return GoalDto.of(goal);
    }

    /** Drops the year's goal for one kind; nothing to drop is not an error. */
    @DELETE
    @Path("/{year}")
    @Transactional
    public void delete(@PathParam("year") int year, @QueryParam("kind") Kind kind) {
        goals.findByUserAndYear(currentUser.id(), year, kind != null ? kind : Kind.BOOK)
                .ifPresent(goals::delete);
    }
}
