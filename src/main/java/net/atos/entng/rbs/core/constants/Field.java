package net.atos.entng.rbs.core.constants;

public class Field {

    private Field() {
        throw new IllegalStateException("Utility class");
    }

    // COMMON

    // id
    public static final String _ID = "_id";
    public static final String ID = "id";

    // structure
    public static final String STRUCTUREID = "structureid";

    // type
    public static final String TYPEID = "typeid";

    // action
    public static final String ACTION = "action";

    // bookings
    public static final String BOOKINGS = "bookings";

    // resource
    public static final String RESOURCE = "resource";
    public static final String RESOURCE_ID = "resource_id";

    // slots
    public static final String SLOTS = "slots";

    // request results
    public static final String STATUS = "status";
    public static final String MESSAGE = "message";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String ERROR = "error";
    public static final String OK = "ok";

    // date
    public static final String DD_MM_YYYY = "dd/MM/YYYY";

    // other
    public static final String VALUE = "value";
    public static final String EXPORT = "export";
    public static final String NAME = "name";
    public static final String DATE = "date";

    // USER

    // userId
    public static final String USERID = "userId";

    public static final String ISOWNER = "isOwner";

}