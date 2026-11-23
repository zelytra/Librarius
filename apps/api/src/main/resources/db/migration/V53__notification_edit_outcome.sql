-- What an EDIT notification says (#634): the outcome at the time it was sent, rather than the
-- proposal's current status, so a notification keeps its wording when the proposal moves on.
-- APPLIED / REJECTED / REVERTED (an admin's decision), VERIFIED (a store page confirmed the
-- book), TRUSTED (applied on the author's trusted standing), UNVERIFIED (the store check failed,
-- the proposal waits for an admin). NULL on the rows written before: they read the status.
ALTER TABLE notification ADD COLUMN edit_outcome VARCHAR(16);
