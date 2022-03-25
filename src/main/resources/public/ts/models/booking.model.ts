import {Moment} from "moment";
import {Resource} from "./resource.model";
import {Data, ResourceType, Structure} from "./resourcetype.model";

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

    booking: Booking;
    booking_reason: String;
    color: String;
    created: String;
    data: Data;
    // days: null
    isBookingInstance: boolean;
    is_periodic: boolean;
    locked: boolean;
    // moderator_id: null
    // moderator_name: null
    modified: String;
    myRights: any;
    // occurrences: null
    owner: String;
    owner_name: String;
    parent_booking_id: number;
    // periodicity: null
    // refusal_reason: null
    resource_id: number;
    status: number;
    periodicEndDate: Date;
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

// export class Availabilities {
//     all : Array<Availability>;
//     validation: boolean;
//     // visibility: null
// }

export class Bookings {
    all: Array<Bookings>;
}