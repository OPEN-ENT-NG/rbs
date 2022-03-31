import {Resource} from "./resource.model";

export class Structure {
    id: string;
    name: string;
}

export interface IResourceTypeResponse {
    id: string;
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
    visibility: boolean;
}

export class ResourceType {
    id: string;
    color: string;
    created: string;
    extendColor: boolean;
    modified: string;
    name: string;
    owner: string;
    schoolId: string;
    shared: Array<any>;
    slotProfile: String;
    validation: boolean;
    visibility: boolean;

    myRights: any;

    build(data: IResourceTypeResponse): ResourceType {
        this.id = data.id;
        this.color = data.color;
        this.created = data.created;
        this.extendColor = data.extendcolor;
        this.modified = data.modified;
        this.name = data.name;
        this.owner = data.owner;
        this.schoolId = data.school_id;
        this.shared = data.shared;
        this.slotProfile = data.slotprofile;
        this.validation = data.validation;
        this.visibility = data.visibility;
        return this;
    }
}

// export class ResourceType {
//     id: string;
//     _id: number;
//     color: string;
//     created: string;
//     extendcolor: boolean;
//     modified: string;
//     name: string;
//     owner: string;
//     school_id: string;
//     shared: Array<any>;
//     slotprofile: String;
//     validation: boolean;
//     // visibility: ;
//
//     data: Data;
//     expanded: boolean;
//     moderators: Array<Moderator>;
//
//     myRights: any;
//     notified: String;
//     resources: {
//         all: Array<Resource>;
//         school_id : String;
//     };
//     structure : Structure;
// }

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
