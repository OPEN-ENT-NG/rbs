import {Resource} from "./resource.model";

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

    booking_reason: String;
    // days: null
    end_date: String;
    isBookingInstance: boolean;
    is_periodic: boolean;
    // moderator_id: null
    // moderator_name: null
    // occurrences: null
    owner_name: String;
    parent_booking_id: number;
    // periodicity: null
    quantity: number;
    // refusal_reason: null
    resource_id: number;
    start_date: String;
    status: number;
}

export class Moderator {
    id: String;
    login: String;
    type: String;
    username: String;
}