import {Moment} from "moment";
import {Resource} from "./resource.model";
import {Data, ResourceType, Structure} from "./resource-type.model";
import Bytes = jest.Bytes;
import {moment} from "entcore";
import {SlotsForAPI} from "./slot.model";

export interface IBookingResponse {
    id: number;
    resource_id: number;
    owner: string;
    booking_reason: string;
    created: string;
    modified: string;
    start_date: string;
    end_date: string;
    status: number;
    moderator_id: string;
    refusal_reason: string;
    parent_booking_id: number;
    days: Bytes;
    periodicity: number;
    occurrences: number;
    is_periodic: boolean;
    quantity: number;
    owner_name: string;
    moderator_name: string;
}

export class Booking {
    id: number;
    resourceId: number;
    owner: string;
    booking_reason: string;
    created: string;
    modified: string;
    start_date: string;
    end_date: string;
    status: number;
    moderatorId: string;
    refusalReason: string;
    parent_booking_id: number;
    days: Bytes;
    periodicity: number;
    occurrences: number;
    is_periodic: boolean;
    quantity: number;
    ownerName: string;
    moderatorName: string;

    type: ResourceType;
    resource: Resource;
    structure: Structure;
    startMoment: Moment;
    endMoment: Moment;
    startTime: Moment;
    endTime: Moment;
    beginning: Moment;
    end: Moment;
    display: any;
    opened: boolean;
    slots: Array<SlotsForAPI>;
    hasBeenDeleted: boolean;

    build(data: IBookingResponse): Booking {
        this.id = data.id;
        this.resourceId = data.resource_id;
        this.owner = data.owner;
        this.booking_reason = data.booking_reason;
        this.created = data.created;
        this.modified = data.modified;
        this.start_date = data.start_date;
        this.end_date = data.end_date;
        this.status = data.status;
        this.moderatorId = data.moderator_id;
        this.refusalReason = data.refusal_reason;
        this.parent_booking_id = data.parent_booking_id;
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

export class Bookings {
    all: Array<Booking>;

    constructor(bookings?: Booking[]) {
        this.all = !!bookings ? bookings : [];
    }
}