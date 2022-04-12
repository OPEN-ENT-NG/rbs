import {Moment} from "moment";
import {Resource} from "./resource.model";
import {Data, ResourceType, Structure} from "./resource-type.model";
import Bytes = jest.Bytes;
import {moment} from "entcore";

export interface IBookingResponse {
    id : number;
    resource_id : number;
    owner : String;
    booking_reason : String;
    created : String;
    modified : String;
    start_date : String;
    end_date : String;
    status : number;
    moderator_id : String;
    refusal_reason : String;
    parent_booking_id : number;
    days : Bytes;
    periodicity : number;
    occurrences : number;
    is_periodic : boolean;
    quantity : number;
    owner_name : String;
    moderator_name : String;
}

export class Booking {
    id : number;
    resourceId : number;
    owner : String;
    bookingReason : String;
    created : String;
    modified : String;
    start_date : String;
    end_date : String;
    status : number;
    moderatorId : String;
    refusalReason : String;
    parentBookingId : number;
    days : Bytes;
    periodicity : number;
    occurrences : number;
    is_periodic : boolean;
    quantity : number;
    ownerName : String;
    moderatorName : String;

    type: ResourceType;
    resource: Resource;
    structure: Structure;
    startMoment: Moment;
    endMoment: Moment;
    startTime: Moment;
    endTime: Moment;
    beginning: Moment;
    end: Moment;
    display: DisplayBooking;

    build(data: IBookingResponse): Booking {
        this.id = data.id;
        this.resourceId = data.resource_id;
        this.owner = data.owner;
        this.bookingReason = data.booking_reason;
        this.created = data.created;
        this.modified = data.modified;
        this.start_date = data.start_date;
        this.end_date = data.end_date;
        this.status = data.status;
        this.moderatorId = data.moderator_id;
        this.refusalReason = data.refusal_reason;
        this.parentBookingId = data.parent_booking_id;
        this.days = data.days;
        this.periodicity = data.periodicity;
        this.occurrences = data.occurrences;
        this.is_periodic = data.is_periodic;
        this.quantity = data.quantity;
        this.ownerName = data.owner_name;
        this.moderatorName = data.moderator_name;

        return this;
    }

    isPast(): boolean {
        return moment(this.startMoment).isBefore(moment());
    }
}

class DisplayBooking {
    STATE_BOOKING: number;
    STATE_PERIODIC: number;
    STATE_RESOURCE: number;
    state: number;
}

export class Bookings {
    all: Array<Booking>;
}