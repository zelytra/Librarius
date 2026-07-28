package zelytra.librarius.web;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import zelytra.librarius.domain.GoalUnit;
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

    @PUT
    @Path("/{year}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public GoalDto upsert(@PathParam("year") int year, @Valid GoalUpsertDto dto) {
        currentUser.require();
        String userId = currentUser.id();
        ReadingGoal goal = goals.findByUserAndYear(userId, year).orElse(null);
        boolean created = goal == null;
        if (created) {
            goal = new ReadingGoal();
            goal.userId = userId;
            goal.year = year;
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
}
