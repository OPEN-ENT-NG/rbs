import {Moment} from "moment";
import moment from "../moment";
import any = jasmine.any;
import {Availabilities, Availability} from "./Availability";

export class Structure {
    id: string;
    name: string;
}

export class ResourceType {
    id: string;
    _id: number;
    color: string;
    created: string;
    extendcolor: boolean;
    modified: string;
    name: string;
    owner: string;
    school_id: string;
    shared: Array<any>;
    slotprofile: String;
    validation: boolean;
    // visibility: ;

    data: Data;
    expanded: boolean;
    moderators: Array<Moderator>;

    myRights: any;
    notified: String;
    resources: {
        all: Array<Resource>;
        school_id : String;
    };
    structure : Structure;
}

export class Resource {
    color: String;
    created: String;
    description: String; //HTML
    icon: any;
    id: number;
    is_available: boolean;
    max_delay: number;
    min_delay: number;
    modified: String;
    name: String;
    owner: String;
    periodic_booking: boolean;
    quantity: number;
    shared: Array<any>;
    type_id: number;
    validation: boolean;
    // visibility: null;

    availabilities: Availabilities;
    bookings: Bookings;
    myRights: any;
    notified: false
    selected: true
    type: ResourceType;
    unavailabilities: Availabilities;
}

export class Booking {
    id: string;
    beginning : Moment;
    display: DisplayBooking;
    end: Moment;
    endDate: Date;
    endMoment: Moment;
    endTime: Moment;
    quantity: number;
    resource: Resource;
    startDate: Date;
    startMoment: Moment;
    startTime: Moment;
    type: ResourceType;
    structure: Structure;
    slotLit: SlotLit;

    end_date: Date;
    iana: String;
    start_date: Date;

    selected: boolean;
}

class DisplayBooking {
    STATE_BOOKING: number;
    STATE_PERIODIC: number;
    STATE_RESOURCE: number;
    state: number;
}

export class SlotLit {
    slots: Array<Slot>;
}

export class Slot {
    beginning : Moment;
    // startMoment : Moment;
    end : Moment;
    // endMoment : Moment;
    iana : Moment;

    id: String;
    endHour: String;
    name: String;
    startHour: String;
}

export class Data {
    color: String;
    created: String;
    extendcolor: boolean;
    id: number;
    modified: String;
    name: String;
    owner: String;
    school_id: String;
    shared: Array<any>;
    slotprofile: String;
    structure: Structure;
    validation: boolean;
    // visibility: null
    _id: number;
}

export class Moderator {
    id: String;
    login: String;
    type: String;
    username: String;
}

// export class Availabilities {
//     all : Array<Availability>;
//     validation: boolean;
//     // visibility: null
// }

export class Bookings {
    all: Array<Bookings>;
}