package net.atos.entng.rbs.core.constants;

import org.entcore.common.http.filter.Trace;

public class Actions {
    private Actions() {
        throw new IllegalStateException("Utility class");
    }

    // Trace actions

    public static final String CREATE_AVAILABILITY = "CREATE_AVAILABILITY";
    public static final String UPDATE_AVAILABILITY = "UPDATE_AVAILABILITY";
    public static final String DELETE_AVAILABILITY = "DELETE_AVAILABILITY";
    public static final String DELETE_ALL_AVAILABILITY = "DELETE_ALL_AVAILABILITY";

    public static final String CREATE_BOOKING = "CREATE_BOOKING";
    public static final String CREATE_PERIODIC_BOOKING = "CREATE_PERIODIC_BOOKING";
    public static final String UPDATE_BOOKING = "UPDATE_BOOKING";
    public static final String UPDATE_PERIODIC_BOOKING = "UPDATE_PERIODIC_BOOKING";
    public static final String PROCESS_BOOKING = "PROCESS_BOOKING";
    public static final String DELETE_BOOKING = "DELETE_BOOKING";
    public static final String EXPORT_ICAL = "EXPORT_ICAL";

    public static final String CREATE_RESOURCE = "CREATE_RESOURCE";
    public static final String UPDATE_RESOURCE = "UPDATE_RESOURCE";
    public static final String DELETE_RESOURCE = "DELETE_RESOURCE";
    public static final String ADD_NOTIFICATION = "ADD_NOTIFICATION";
    public static final String REMOVE_NOTIFICATION = "REMOVE_NOTIFICATION";
    public static final String SHARE_RESOURCE_SUBMIT = "SHARE_RESOURCE_SUBMIT";
    public static final String SHARE_RESOURCE_REMOVE = "SHARE_RESOURCE_REMOVE";

    public static final String CREATE_RESOURCE_TYPE = "CREATE_RESOURCE_TYPE";
    public static final String UPDATE_RESOURCE_TYPE = "UPDATE_RESOURCE_TYPE";
    public static final String DELETE_RESOURCE_TYPE = "DELETE_RESOURCE_TYPE";
    public static final String SHARE_RESOURCE_TYPE_SUBMIT = "SHARE_RESOURCE_TYPE_SUBMIT";
    public static final String SHARE_RESOURCE_TYPE_REMOVE = "SHARE_RESOURCE_TYPE_REMOVE";
    public static final String ADD_NOTIFICATIONS = "ADD_NOTIFICATIONS";
    public static final String REMOVE_NOTIFICATIONS = "REMOVE_NOTIFICATIONS";
    public static final String SHARE_RESOURCE_TYPE = "SHARE_RESOURCE_TYPE";
}
