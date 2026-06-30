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

/** Objectifs de lecture annuels de l'utilisateur. */
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
        ReadingGoal goal = goals.findByUserAndYear(userId, year).orElseGet(() -> {
            ReadingGoal g = new ReadingGoal();
            g.userId = userId;
            g.year = year;
            goals.persist(g);
            return g;
        });
        goal.targetCount = dto.targetCount();
        goal.unit = dto.unit() != null ? dto.unit() : GoalUnit.BOOKS;
        return GoalDto.of(goal);
    }
}
