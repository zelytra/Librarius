package zelytra.librarius.domain;

/** Publication status of a series, as reported by the catalog provider. */
public enum SeriesStatus {
    /** Still being published: new volumes are expected. */
    ONGOING,
    /** Finished: the run is complete. */
    COMPLETED,
    /** Paused by its author or publisher, with no announced resumption. */
    HIATUS
}
